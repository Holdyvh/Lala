package com.lala.assistant.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receptor para iniciar el servicio del asistente automáticamente
 * cuando el dispositivo se reinicia.
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Dispositivo reiniciado, iniciando servicio del asistente")
            
            try {
                // Iniciar el servicio del asistente
                val serviceIntent = Intent(context, AssistantService::class.java)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                
                Log.d(TAG, "Servicio del asistente iniciado después del reinicio")
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar servicio después del reinicio", e)
            }
        }
    }
}