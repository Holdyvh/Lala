package com.lala.assistant.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Gestiona la obtención de la ubicación mediante Google Play Services
 */
class LocationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LocationManager"
    }
    
    // Cliente de ubicación fusionado
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    /**
     * Verifica si se tienen los permisos necesarios para obtener la ubicación
     */
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || 
        ActivityCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Obtiene la última ubicación conocida
     */
    suspend fun getLastLocation(): Result<Location> = suspendCancellableCoroutine { continuation ->
        // Verificar permisos
        if (!hasLocationPermission()) {
            continuation.resume(Result.failure(Exception("No se tienen permisos de ubicación")))
            return@suspendCancellableCoroutine
        }
        
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(Result.success(location))
                    } else {
                        continuation.resume(Result.failure(
                            Exception("No se pudo obtener la última ubicación. Intenta de nuevo.")
                        ))
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al obtener la última ubicación", e)
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al obtener la última ubicación", e)
            continuation.resume(Result.failure(e))
        }
        
        continuation.invokeOnCancellation {
            // No hay nada específico que cancelar
        }
    }
    
    /**
     * Obtiene la ubicación actual (más precisa pero puede tardar más)
     */
    suspend fun getCurrentLocation(): Result<Location> = suspendCancellableCoroutine { continuation ->
        // Verificar permisos
        if (!hasLocationPermission()) {
            continuation.resume(Result.failure(Exception("No se tienen permisos de ubicación")))
            return@suspendCancellableCoroutine
        }
        
        try {
            val cancellationToken = object : CancellationToken() {
                private val tokenSource = CancellationTokenSource()
                
                override fun onCanceledRequested(listener: OnTokenCanceledListener) =
                    tokenSource.token.onCanceledRequested(listener)
                
                override fun isCancellationRequested() = tokenSource.token.isCancellationRequested
            }
            
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken
            )
            .addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(Result.success(location))
                } else {
                    continuation.resume(Result.failure(
                        Exception("No se pudo obtener la ubicación actual. Intenta de nuevo.")
                    ))
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener la ubicación actual", e)
                continuation.resume(Result.failure(e))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al obtener la ubicación actual", e)
            continuation.resume(Result.failure(e))
        }
        
        continuation.invokeOnCancellation {
            // No es necesario cancelar explícitamente
        }
    }
    
    /**
     * Obtiene un texto descriptivo de la ubicación para consultas externas
     */
    suspend fun getLocationString(): String {
        return try {
            val locationResult = getLastLocation()
            
            if (locationResult.isSuccess) {
                val location = locationResult.getOrNull()
                if (location != null) {
                    "${location.latitude},${location.longitude}"
                } else {
                    "ubicación desconocida"
                }
            } else {
                "ubicación desconocida"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener texto de ubicación", e)
            "ubicación desconocida"
        }
    }
}