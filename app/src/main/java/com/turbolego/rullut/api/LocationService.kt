package com.turbolego.rullut.api

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.turbolego.rullut.map.MapConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Location service for GPS tracking.
 * Uses FusedLocationProviderClient (Google Play Services).
 *
 * Mirrors the Expo app's expo-location features:
 * - Current location (one-shot)
 * - Live location updates (Flow)
 * - GPS accuracy (high)
 * - Location permission checks
 */
object LocationService {

    private const val TAG = "LocationService"
    private const val UPDATE_INTERVAL_MS = 5_000L // 5 seconds
    private const val FASTEST_INTERVAL_MS = 2_000L // 2 seconds

    private var fusedLocationClient: FusedLocationProviderClient? = null

    private fun getClient(context: Context): FusedLocationProviderClient {
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        }
        return fusedLocationClient!!
    }

    /**
     * Check if fine location permission is granted.
     */
    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get the last known location (fast, may be stale or null).
     */
    suspend fun getLastKnownLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        return suspendCancellableCoroutine { continuation ->
            try {
                getClient(context).lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location, onCancellation = null)
                    }
                    .addOnFailureListener {
                        continuation.resume(null, onCancellation = null)
                    }
            } catch (_: Exception) {
                continuation.resume(null, onCancellation = null)
            }
        }
    }

    /**
     * Get current location (one-shot, requests fresh fix).
     */
    suspend fun getCurrentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        return suspendCancellableCoroutine { continuation ->
            try {
                val request = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    100L // minimal time, we just want one
                ).setMaxUpdates(1).build()

                getClient(context).getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).addOnSuccessListener { location ->
                    continuation.resume(location, onCancellation = null)
                }.addOnFailureListener {
                    continuation.resume(null, onCancellation = null)
                }
            } catch (_: Exception) {
                continuation.resume(null, onCancellation = null)
            }
        }
    }

    /**
     * Live location updates as a Flow.
     * Emits Location objects at ~5s intervals.
     * Stops when the flow collection is cancelled.
     */
    fun locationUpdates(context: Context): Flow<Location> = callbackFlow {
        if (!hasPermission(context)) {
            Log.w(TAG, "Location permission not granted")
            close(Exception("Location permission not granted"))
            return@callbackFlow
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    trySend(location)
                }
            }
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS,
        ).setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setMaxUpdates(Int.MAX_VALUE.toLong())
            .build()

        try {
            getClient(context).requestLocationUpdates(
                locationRequest,
                callback,
                context.mainLooper,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location updates", e)
            close(e)
            return@callbackFlow
        }

        awaitClose {
            try {
                getClient(context).removeLocationUpdates(callback)
            } catch (_: Exception) { }
        }
    }
}