package com.lala.assistant.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lala.assistant.R
import com.lala.assistant.ui.MainActivity

/**
 * Servicio para manejar notificaciones push de Firebase
 */
class FirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "lala_notifications_channel"
        private const val NOTIFICATION_ID = 2001
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Mensaje recibido de: ${remoteMessage.from}")
        
        // Verificar si el mensaje contiene datos
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Datos del mensaje: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }
        
        // Verificar si el mensaje contiene notificación
        remoteMessage.notification?.let {
            Log.d(TAG, "Cuerpo de notificación: ${it.body}")
            handleNotification(it.title, it.body)
        }
    }
    
    override fun onNewToken(token: String) {
        Log.d(TAG, "Nuevo token FCM: $token")
        
        // Aquí se puede enviar el token al servidor de la aplicación
        // para asociarlo con el usuario actual y enviar notificaciones
    }
    
    /**
     * Maneja mensajes con datos
     */
    private fun handleDataMessage(data: Map<String, String>) {
        val title = data["title"] ?: "Lala Assistant"
        val message = data["message"] ?: "Tienes una nueva notificación"
        val type = data["type"] ?: "general"
        
        // Dependiendo del tipo, podemos realizar diferentes acciones
        when (type) {
            "reminder" -> {
                // Mostrar recordatorio
                sendNotification(title, message)
                
                // Opcionalmente, anunciar por voz si el asistente está activo
                val serviceIntent = Intent(this, AssistantService::class.java).apply {
                    putExtra("ACTION", "ANNOUNCE")
                    putExtra("MESSAGE", message)
                }
                startService(serviceIntent)
            }
            "sync" -> {
                // Iniciar sincronización de datos
                val serviceIntent = Intent(this, AssistantService::class.java).apply {
                    putExtra("ACTION", "SYNC")
                }
                startService(serviceIntent)
            }
            else -> {
                // Notificación general
                sendNotification(title, message)
            }
        }
    }
    
    /**
     * Maneja mensajes de notificación simples
     */
    private fun handleNotification(title: String?, body: String?) {
        val notificationTitle = title ?: "Lala Assistant"
        val notificationBody = body ?: "Tienes una nueva notificación"
        
        sendNotification(notificationTitle, notificationBody)
    }
    
    /**
     * Envía una notificación al usuario
     */
    private fun sendNotification(title: String, message: String) {
        // Intent para abrir la app al tocar la notificación
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Configurar canal de notificación (requerido en Android 8.0+)
        createNotificationChannel()
        
        // Sonido de notificación
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        // Construir notificación
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        // Mostrar notificación
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }
    
    /**
     * Crea el canal de notificación para Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notificaciones de Lala"
            val descriptionText = "Canal para notificaciones de Lala Assistant"
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}