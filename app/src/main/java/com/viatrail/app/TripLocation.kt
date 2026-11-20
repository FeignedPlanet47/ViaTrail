package com.viatrail.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.viatrail.domain.Point
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object TripLocation {
    fun hasPermission(context:Context) = ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    suspend fun current(context:Context):Point? = suspendCancellableCoroutine { cont ->
        if(!hasPermission(context)) { cont.resume(null); return@suspendCancellableCoroutine }
        val client=LocationServices.getFusedLocationProviderClient(context)
        try {
            val cancellation=com.google.android.gms.tasks.CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY,cancellation.token)
                .addOnSuccessListener { location -> if(cont.isActive) cont.resume(location?.let { Point(it.latitude,it.longitude) }) }
                .addOnFailureListener { if(cont.isActive) cont.resume(null) }
            cont.invokeOnCancellation { cancellation.cancel() }
        } catch (_:SecurityException) { if(cont.isActive) cont.resume(null) }
    }
}
