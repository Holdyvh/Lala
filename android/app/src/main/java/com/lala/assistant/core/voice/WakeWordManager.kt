package com.lala.assistant.core.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.lala.assistant.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WakeWordManager: Componente para detectar palabras clave como "Hey Lala"
 * 
 * Detecta palabras clave en tiempo real utilizando un modelo ligero optimizado 
 * para dispositivos móviles. Soporta múltiples palabras clave y es tolerante
 * a variaciones en la pronunciación.
 */
class WakeWordManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "WakeWordManager"
        private const val SAMPLE_RATE = 16000
        private const val DEFAULT_WAKE_WORD = "Hey Lala"
        private const val BUFFER_SIZE = 4096
    }
    
    // Palabra clave actual (personalizable)
    private var wakeWord = DEFAULT_WAKE_WORD
    
    // Callback para notificar cuando se detecta la palabra clave
    private var wakeWordListener: ((String) -> Unit)? = null
    
    // AudioRecord para captura continua
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var detectionJob: Job? = null
    
    // Modelo para detección de palabra clave
    private var wakeWordModel: Any? = null
    private var isModelInitialized = false
    
    init {
        // Inicializar el modelo en segundo plano
        initModel()
    }
    
    /**
     * Inicializa el modelo de detección
     */
    private fun initModel() {
        Thread {
            try {
                // TODO: Implementar inicialización real del modelo
                // Ejemplo con PocketSphinx (simulado para desarrollo):
                // val assets = Assets(context)
                // val assetDir = assets.syncAssets()
                // val config = Decoder.defaultConfig()
                // config.setString("-hmm", "$assetDir/en-us-ptm")
                // config.setString("-dict", "$assetDir/cmudict-en-us.dict")
                // config.setString("-keyphrase", wakeWord.toLowerCase())
                // config.setFloat("-kws_threshold", 1e-20f)
                // wakeWordModel = Decoder(config)
                
                // Simulación para desarrollo
                Log.d(TAG, "Modelo de palabra clave inicializado (simulado)")
                Thread.sleep(500) // Simular tiempo de carga
                isModelInitialized = true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error al inicializar modelo de palabra clave", e)
                isModelInitialized = false
            }
        }.start()
    }
    
    /**
     * Establece el listener para notificaciones de detección
     */
    fun setWakeWordListener(listener: (String) -> Unit) {
        this.wakeWordListener = listener
    }
    
    /**
     * Establece una nueva palabra clave
     */
    fun setWakeWord(word: String) {
        if (word.isNotEmpty() && word != this.wakeWord) {
            Log.d(TAG, "Cambiando palabra clave a: $word")
            this.wakeWord = word
            
            // Reiniciar modelo con nueva palabra clave
            if (isListening) {
                stopListening()
                initModel()
                startListening()
            } else {
                initModel()
            }
        }
    }
    
    /**
     * Inicia la detección continua de la palabra clave
     */
    fun startListening() {
        if (isListening) return
        
        try {
            // Crear AudioRecord para captura
            audioRecord = createAudioRecord()
            
            val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
                .order(ByteOrder.nativeOrder())
            
            audioRecord?.startRecording()
            isListening = true
            
            // Iniciar detección en coroutine para no bloquear el hilo principal
            detectionJob = CoroutineScope(Dispatchers.Default).launch {
                while (isListening) {
                    try {
                        // Leer audio
                        buffer.clear()
                        val read = audioRecord?.read(buffer, BUFFER_SIZE) ?: 0
                        
                        if (read > 0) {
                            // Procesar audio para detección
                            detectWakeWord(buffer)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en detección de palabra clave", e)
                    }
                }
            }
            
            Log.d(TAG, "Detección de palabra clave iniciada")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar detección de palabra clave", e)
            releaseAudioRecord()
        }
    }
    
    /**
     * Detiene la detección de palabra clave
     */
    fun stopListening() {
        if (!isListening) return
        
        isListening = false
        detectionJob?.cancel()
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener grabación", e)
        } finally {
            releaseAudioRecord()
        }
        
        Log.d(TAG, "Detección de palabra clave detenida")
    }
    
    /**
     * Procesa el buffer de audio para detectar la palabra clave
     */
    private fun detectWakeWord(buffer: ByteBuffer) {
        if (!isModelInitialized) return
        
        // TODO: Implementar detección real con el modelo
        // Ejemplo con PocketSphinx (simulado para desarrollo):
        // val decoder = wakeWordModel as Decoder
        // decoder.processRaw(buffer, buffer.capacity(), false, false)
        // if (decoder.hyp() != null) {
        //     notifyWakeWordDetected()
        // }
        
        // Simulación para desarrollo (detección aleatoria cada ~30 segundos)
        if (Math.random() < 0.0005) {
            notifyWakeWordDetected()
        }
    }
    
    /**
     * Notifica que se ha detectado la palabra clave
     */
    private fun notifyWakeWordDetected() {
        Log.d(TAG, "¡Palabra clave detectada: $wakeWord!")
        wakeWordListener?.invoke(wakeWord)
    }
    
    /**
     * Crea un AudioRecord para la captura continua
     */
    private fun createAudioRecord(): AudioRecord {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        return AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(BUFFER_SIZE, minBufferSize)
        )
    }
    
    /**
     * Libera recursos de AudioRecord
     */
    private fun releaseAudioRecord() {
        try {
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar AudioRecord", e)
        }
    }
    
    /**
     * Libera todos los recursos
     */
    fun release() {
        stopListening()
        wakeWordModel = null
        isModelInitialized = false
    }
}