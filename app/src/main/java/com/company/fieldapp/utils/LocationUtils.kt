package com.company.fieldapp.utils

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices

object LocationUtils {

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(
        context: Context,
        onLocationReceived: (Double, Double) -> Unit,
        onError: (String) -> Unit
    ) {
        val fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(context)

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    onLocationReceived(location.latitude, location.longitude)
                } else {
                    onError("Location unavailable")
                }
            }
            .addOnFailureListener {
                onError(it.message ?: "Location error")
            }
    }
}
