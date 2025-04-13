package com.lala.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lala.assistant.R
import com.lala.assistant.core.agent.AgentManager
import com.lala.assistant.core.agent.MemoryModule
import com.lala.assistant.core.agent.TaskPlanner
import com.lala.assistant.core.voice.SpeechRecognizer
import com.lala.assistant.core.voice.VoiceManager
import com.lala.assistant.core.voice.WakeWordDetector
import com.lala.assistant.data.remote.ApiService
import com.lala.assistant.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano para mantener Lala ejecutándose en segundo plano
 * y estar disponible para comandos de voz continuamente.
 */
class AssistantService : Service() {

    companion object {
        private const val TAG = "AssistantService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "lala_assistant_channel"
    }

    // Componentes de Lala
    private lateinit var voiceManager: VoiceManager
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var agentManager: AgentManager
    private lateinit var apiService: ApiService
    
    // Binder para comunicación con actividades
    private val binder = LocalBinder()
    
    // Scope para corrutinas del servicio
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Iniciando servicio del asistente")
        
        // Inicializar componentes
        initializeComponents()
        
        // Mostrar notificación para mantener el servicio activo
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Iniciar escucha en segundo plano
        startBackgroundListening()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio del asistente recibió comando de inicio")
        return START_STICKY // Reiniciar si es terminado por el sistema
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        Log.d(TAG, "Servicio del asistente terminado")
        
        // Detener escucha en segundo plano
        voiceManager.stopBackgroundListening()
        
        // Liberar recursos
        voiceManager.shutdown()
    }
    
    /**
     * Inicializa los componentes principales del asistente
     */
    private fun initializeComponents() {
        try {
            // Crear instancias de componentes
            speechRecognizer = SpeechRecognizer(this)
            wakeWordDetector = WakeWordDetector(this)
            voiceManager = VoiceManager(this, speechRecognizer, wakeWordDetector)
            apiService = ApiService(this)
            
            // Inicializar sistema de voz con callback
            voiceManager.initialize {
                Log.d(TAG, "Sistema de voz inicializado")
            }
            
            // Inicializar agente de IA
            val database = (applicationContext as com.lala.assistant.App).database
            val memoryModule = MemoryModule(database.memoryDao())
            val taskPlanner = TaskPlanner()
            
            agentManager = AgentManager(
                taskPlanner = taskPlanner,
                memoryModule = memoryModule,
                apiService = apiService,
                context = this
            )
            
            Log.d(TAG, "Componentes del asistente inicializados")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar componentes", e)
        }
    }
    
    /**
     * Inicia la escucha continua en segundo plano para detección de palabra clave
     */
    private fun startBackgroundListening() {
        try {
            // Iniciar escucha de palabra clave
            voiceManager.startBackgroundListening {
                // Se detectó la palabra clave, iniciar procesamiento de comando
                Log.d(TAG, "Palabra clave detectada, iniciando escucha activa")
                
                // Usar coroutine para operaciones asíncronas
                serviceScope.launch {
                    try {
                        // Feedback de reconocimiento (sonido o vibración)
                        
                        // Escuchar comando
                        val command = voiceManager.listenForCommand()
                        
                        if (!command.isNullOrBlank()) {
                            Log.d(TAG, "Comando reconocido: $command")
                            
                            // Procesar comando con el agente
                            val response = agentManager.processCommand(command)
                            
                            // Responder al usuario
                            voiceManager.speak(response)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar comando por voz", e)
                        voiceManager.speak("Lo siento, ocurrió un error al procesar tu comando.")
                    }
                }
            }
            
            Log.d(TAG, "Escucha en segundo plano iniciada")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar escucha en segundo plano", e)
        }
    }
    
    /**
     * Crea la notificación persistente para el servicio en primer plano
     */
    private fun createNotification(): Notification {
        // Crear canal de notificación (requerido en Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Lala Assistant"
            val descriptionText = "Mantiene Lala activo y disponible"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // Intent para abrir la app al tocar la notificación
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Construir notificación
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lala Assistant")
            .setContentText("Estoy escuchando y lista para ayudarte")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Clase binder para comunicación con actividades
     */
    inner class LocalBinder : Binder() {
        fun getService(): AssistantService = this@AssistantService
        
        // Acceso a los componentes para comunicación desde la UI
        fun getVoiceManager(): VoiceManager = voiceManager
        fun getAgentManager(): AgentManager = agentManager
    }
}