package com.lalaassistant.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.atomic.AtomicBoolean;

public class LalaBackgroundService extends Service {
    private static final String TAG = "LalaBackgroundService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "lala_assistant_channel";
    
    // Flag to track if the service is running
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // Wake lock to keep the CPU running
    private PowerManager.WakeLock wakeLock;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Create the notification channel for Android O and above
        createNotificationChannel();
        
        // Acquire a wake lock to keep the CPU running
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LalaAssistant::BackgroundServiceWakeLock"
        );
        
        Log.d(TAG, "Background service created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Background service started");
        
        // Start as a foreground service with a notification
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Acquire the wake lock
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        
        // Set the running flag
        isRunning.set(true);
        
        // Start monitoring for the wake word
        startWakeWordMonitoring();
        
        // Return START_STICKY to ensure the service is restarted if it's killed
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Background service destroyed");
        
        // Stop monitoring for the wake word
        stopWakeWordMonitoring();
        
        // Release the wake lock
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        // Set the running flag
        isRunning.set(false);
        
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // This service doesn't support binding
        return null;
    }
    
    /**
     * Create the notification channel for Android O and above
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Lala Assistant",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Used to keep Lala Assistant running in the background");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Create the notification for the foreground service
     * @return The created notification
     */
    private Notification createNotification() {
        // Create an intent to open the app when the notification is tapped
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Lala Assistant")
                .setContentText("Escuchando en segundo plano")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        
        return builder.build();
    }
    
    /**
     * Start monitoring for the wake word
     */
    private void startWakeWordMonitoring() {
        // In a real implementation, this would start a background thread
        // to listen for the wake word using a third-party library like Porcupine
        // or a custom wake word detection implementation
        
        Log.d(TAG, "Started wake word monitoring");
    }
    
    /**
     * Stop monitoring for the wake word
     */
    private void stopWakeWordMonitoring() {
        // In a real implementation, this would stop the background thread
        // and release any resources used for wake word detection
        
        Log.d(TAG, "Stopped wake word monitoring");
    }
    
    /**
     * Check if the service is running
     * @return True if the service is running, false otherwise
     */
    public static boolean isRunning() {
        return isRunning.get();
    }
}
