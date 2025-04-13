package com.lala.assistant.core.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Detector de palabra clave en segundo plano
 */
class WakeWordDetector(private val context: Context) {

    companion object {
        private const val TAG = "WakeWordDetector"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE_IN_SECONDS = 0.5 // 500ms de buffer
        private const val DEFAULT_WAKE_WORD = "lala"
    }
    
    // Configuración de audio
    private val audioSource = MediaRecorder.AudioSource.MIC
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    // Tamaño del buffer en bytes
    private val bufferSize = (SAMPLE_RATE * BUFFER_SIZE_IN_SECONDS * 2).toInt() // 16 bits = 2 bytes
    
    private var audioRecord: AudioRecord? = null
    private var voskModel: Model? = null
    private var recognizer: Recognizer? = null
    
    private var isListening = false
    private var detectionThread: Thread? = null
    
    // Palabra clave a detectar
    private var wakeWord = DEFAULT_WAKE_WORD
    
    /**
     * Inicializa el detector de palabra clave
     */
    fun initialize() {
        try {
            // Cargar modelo Vosk pequeño para detección de palabra clave
            val appDir = context.getExternalFilesDir(null)
            val modelDir = File(appDir, "vosk-model-small-es")
            
            if (modelDir.exists()) {
                Log.d(TAG, "Cargando modelo para detección de palabra clave")
                voskModel = Model(modelDir.absolutePath)
                
                if (voskModel != null) {
                    // Inicializar reconocedor con configuración para palabra clave
                    recognizer = Recognizer(voskModel, SAMPLE_RATE.toFloat(), 
                        "[$wakeWord]") // Formato de gramática Vosk para palabra clave
                    
                    Log.d(TAG, "Detector de palabra clave inicializado con éxito")
                }
            } else {
                Log.e(TAG, "No se encontró el modelo para detector de palabra clave")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error al inicializar detector de palabra clave", e)
        }
    }
    
    /**
     * Configura la palabra clave a detectar
     */
    fun setWakeWord(word: String) {
        if (word.isNotEmpty() && word != wakeWord) {
            wakeWord = word.toLowerCase()
            
            // Reinicializar reconocedor con nueva palabra clave
            if (voskModel != null) {
                recognizer?.close()
                recognizer = Recognizer(voskModel, SAMPLE_RATE.toFloat(), "[$wakeWord]")
                Log.d(TAG, "Palabra clave actualizada a: $wakeWord")
            }
        }
    }
    
    /**
     * Inicia la detección de palabra clave en segundo plano
     */
    fun startDetection(onWakeWordDetected: () -> Unit) {
        if (isListening) {
            Log.w(TAG, "La detección de palabra clave ya está activa")
            return
        }
        
        if (recognizer == null) {
            Log.e(TAG, "No se puede iniciar detección: reconocedor no inicializado")
            return
        }
        
        try {
            // Inicializar grabación de audio
            audioRecord = AudioRecord(
                audioSource,
                SAMPLE_RATE,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            // Verificar si se creó correctamente
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "No se pudo inicializar AudioRecord")
                audioRecord?.release()
                audioRecord = null
                return
            }
            
            isListening = true
            
            // Iniciar grabación
            audioRecord?.startRecording()
            
            // Crear hilo para escuchar continuamente
            detectionThread = Thread {
                val buffer = ByteBuffer.allocateDirect(bufferSize)
                buffer.order(ByteOrder.nativeOrder())
                
                val audioData = ByteArray(bufferSize)
                
                // Bucle de detección continua
                while (isListening) {
                    try {
                        // Leer audio del micrófono
                        val bytesRead = audioRecord?.read(buffer, bufferSize) ?: -1
                        
                        if (bytesRead > 0) {
                            // Procesar audio con Vosk
                            buffer.rewind()
                            val result = if (recognizer?.acceptWaveForm(buffer, bytesRead) == true) {
                                recognizer?.result ?: ""
                            } else {
                                recognizer?.partialResult ?: ""
                            }
                            
                            // Verificar si se detectó la palabra clave
                            if (result.contains(wakeWord, ignoreCase = true)) {
                                Log.d(TAG, "¡Palabra clave detectada! $wakeWord")
                                
                                // Notificar en hilo principal
                                CoroutineScope(Dispatchers.Main).launch {
                                    onWakeWordDetected()
                                }
                                
                                // Breve pausa para evitar detecciones repetidas
                                Thread.sleep(1000)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en bucle de detección de palabra clave", e)
                        isListening = false
                    }
                }
            }
            
            // Iniciar hilo de detección
            detectionThread?.start()
            
            Log.d(TAG, "Detección de palabra clave iniciada")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar detección de palabra clave", e)
            stopDetection()
        }
    }
    
    /**
     * Detiene la detección de palabra clave
     */
    fun stopDetection() {
        isListening = false
        
        try {
            // Detener grabación y liberar recursos
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            // Esperar a que termine el hilo
            detectionThread?.join(500)
            detectionThread = null
            
            Log.d(TAG, "Detección de palabra clave detenida")
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener detección de palabra clave", e)
        }
    }
    
    /**
     * Libera recursos
     */
    fun shutdown() {
        stopDetection()
        recognizer?.close()
        voskModel?.close()
    }
}