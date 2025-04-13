package com.lala.assistant

import android.app.Application
import android.content.Intent
import android.util.Log
import com.lala.assistant.data.local.AppDatabase
import com.lala.assistant.services.AssistantService
import com.lala.assistant.ui.theme.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Clase de aplicación principal para Lala
 */
class App : Application() {
    
    // Scope global para corrutinas de aplicación
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Acceso lazy a la base de datos
    val database by lazy { AppDatabase.getDatabase(this) }
    
    override fun onCreate() {
        super.onCreate()
        
        // Inicializar Timber para logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Inicializar tema
        ThemeManager.initialize(this)
        
        // Iniciar servicios de la aplicación
        applicationScope.launch {
            startAssistantService()
        }
    }
    
    /**
     * Inicia el servicio del asistente en segundo plano
     */
    private fun startAssistantService() {
        try {
            val serviceIntent = Intent(this, AssistantService::class.java)
            startForegroundService(serviceIntent)
            Log.d(TAG, "Servicio del asistente iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar servicio del asistente", e)
        }
    }
    
    companion object {
        private const val TAG = "LalaApp"
    }
}