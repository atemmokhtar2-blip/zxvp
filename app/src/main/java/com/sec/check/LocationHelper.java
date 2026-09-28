package com.sec.check;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LocationHelper - يدعم الموقع بكل الطرق
 * 1. FusedLocationProviderClient (Google Play Services)
 * 2. LocationManager (fallback)
 */
public class LocationHelper {

    private static final String TAG = "LocationHelper";

    public interface LocationCallback2 {
        void onSuccess(double lat, double lng, float accuracy, String provider);
        void onError(String error);
    }

    // ============================================================
    // ★★★ الطريقة الرئيسية ★★★
    // ============================================================
    public static void getLocation(Context context, LocationCallback2 callback) {
        // فحص الصلاحيات
        boolean hasFine = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFine && !hasCoarse) {
            callback.onError("no_location_permission");
            return;
        }

        // جرّب Fused Location First (أسرع وأدق)
        try {
            getLocationFused(context, callback);
            return;
        } catch (Exception e) {
            Log.w(TAG, "Fused location failed: " + e.getMessage());
        }

        // Fallback: LocationManager
        getLocationManager(context, callback);
    }

    // ============================================================
    // ★★★ Fused Location (Google Play Services) ★★★
    // ============================================================
    private static void getLocationFused(Context context, LocationCallback2 callback) {
        try {
            FusedLocationProviderClient fusedClient =
                    LocationServices.getFusedLocationProviderClient(context);

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Location> locationRef = new AtomicReference<>(null);

            LocationRequest request = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 1000)
                    .setWaitForAccurateLocation(false)
                    .setMinUpdateIntervalMillis(1000)
                    .setMaxUpdateDelayMillis(15000)
                    .setMaxUpdates(1)
                    .build();

            LocationCallback locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null) {
                        Location loc = locationResult.getLastLocation();
                        if (loc != null) {
                            locationRef.set(loc);
                        }
                    }
                    latch.countDown();
                }
            };

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {

                fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());

                // انتظر 15 ثانية
                boolean completed = latch.await(15, TimeUnit.SECONDS);

                try {
                    fusedClient.removeLocationUpdates(locationCallback);
                } catch (Exception e) {}

                Location loc = locationRef.get();
                if (loc != null) {
                    callback.onSuccess(
                            loc.getLatitude(),
                            loc.getLongitude(),
                            loc.getAccuracy(),
                            "fused"
                    );
                    Log.d(TAG, "Fused location: " + loc.getLatitude() + ", " + loc.getLongitude());
                } else {
                    callback.onError("no_fused_location");
                }
            } else {
                callback.onError("no_fine_permission");
            }

        } catch (Exception e) {
            Log.e(TAG, "getLocationFused error: " + e.getMessage());
            callback.onError("fused_exception: " + e.getMessage());
        }
    }

    // ============================================================
    // ★★★ LocationManager (Fallback) ★★★
    // ============================================================
    private static void getLocationManager(Context context, LocationCallback2 callback) {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                callback.onError("no_location_manager");
                return;
            }

            // جرّب آخر موقع معروف أولاً
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
                    Log.w(TAG, "SecurityException for " + provider);
                }
            }

            // لو لقينا موقع معروف
            if (bestLocation != null) {
                callback.onSuccess(
                        bestLocation.getLatitude(),
                        bestLocation.getLongitude(),
                        bestLocation.getAccuracy(),
                        bestLocation.getProvider() + "_cached"
                );
                Log.d(TAG, "Cached location: " + bestLocation.getLatitude());
                return;
            }

            // مفيش → نطلب موقع جديد
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Location> freshLoc = new AtomicReference<>(null);

            android.location.LocationListener listener = new android.location.LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    freshLoc.set(location);
                    latch.countDown();
                }
                @Override
                public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
                @Override
                public void onProviderEnabled(String provider) {}
                @Override
                public void onProviderDisabled(String provider) {}
            };

            boolean requested = false;
            for (String provider : providers) {
                try {
                    if (lm.isProviderEnabled(provider)) {
                        lm.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper());
                        requested = true;
                        Log.d(TAG, "Requested updates from: " + provider);
                    }
                } catch (SecurityException se) {
                    Log.w(TAG, "Cannot request from " + provider);
                }
            }

            if (!requested) {
                callback.onError("no_provider_enabled");
                return;
            }

            // انتظر 20 ثانية
            latch.await(20, TimeUnit.SECONDS);

            try {
                lm.removeUpdates(listener);
            } catch (Exception e) {}

            Location loc = freshLoc.get();
            if (loc != null) {
                callback.onSuccess(
                        loc.getLatitude(),
                        loc.getLongitude(),
                        loc.getAccuracy(),
                        loc.getProvider()
                );
                Log.d(TAG, "Fresh location: " + loc.getLatitude());
            } else {
                callback.onError("no_location");
            }

        } catch (Exception e) {
            Log.e(TAG, "getLocationManager error: " + e.getMessage());
            callback.onError("exception: " + e.getMessage());
        }
    }

    // ============================================================
    // Reverse Geocoding
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
            Log.w(TAG, "geocoder error: " + e.getMessage());
        }
        return null;
    }
                      }
