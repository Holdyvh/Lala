package com.lala.assistant.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Gestiona todas las interacciones de voz (entrada y salida) del asistente
 */
class VoiceManager(
    private val context: Context,
    private val speechRecognizer: SpeechRecognizer,
    private val wakeWordDetector: WakeWordDetector
) {

    companion object {
        private const val TAG = "VoiceManager"
    }
    
    // Instancia de Text-to-Speech
    private lateinit var textToSpeech: TextToSpeech
    
    // Estados de voz
    enum class State {
        IDLE,           // En espera
        LISTENING,      // Escuchando al usuario
        PROCESSING,     // Procesando comando
        SPEAKING        // Hablando
    }
    
    // Estado actual
    private val _currentState = MutableStateFlow(State.IDLE)
    val currentState: StateFlow<State> = _currentState
    
    // Indica si el Text-to-Speech está inicializado correctamente
    private var isTtsReady = false
    
    /**
     * Inicializa el sistema de síntesis de voz
     */
    fun initialize(onReady: () -> Unit) {
        // Inicializar Text-to-Speech
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale("es", "ES"))
                
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    
                    Log.e(TAG, "Idioma no soportado, usando idioma por defecto")
                    textToSpeech.setLanguage(Locale.getDefault())
                }
                
                textToSpeech.setSpeechRate(1.0f)
                textToSpeech.setPitch(1.0f)
                
                // Configurar listener para eventos de TTS
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _currentState.value = State.SPEAKING
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        _currentState.value = State.IDLE
                    }
                    
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "Error en la síntesis de voz: $utteranceId")
                        _currentState.value = State.IDLE
                    }
                })
                
                isTtsReady = true
                onReady()
            } else {
                Log.e(TAG, "Error al inicializar Text-to-Speech: $status")
            }
        }
        
        // Inicializar reconocedor de voz
        speechRecognizer.initialize()
        
        // Inicializar detector de palabra clave
        wakeWordDetector.initialize()
    }
    
    /**
     * Libera recursos cuando no se necesitan
     */
    fun shutdown() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        
        speechRecognizer.shutdown()
        wakeWordDetector.shutdown()
    }
    
    /**
     * Sintetiza texto a voz
     */
    fun speak(text: String, utteranceId: String = "speech_${System.currentTimeMillis()}") {
        if (isTtsReady) {
            // Detener cualquier voz en curso
            textToSpeech.stop()
            
            // Hablar nuevo texto
            textToSpeech.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )
        } else {
            Log.e(TAG, "Text-to-Speech no está inicializado correctamente")
        }
    }
    
    /**
     * Sintetiza texto a voz y espera hasta que termine (para usar en coroutines)
     */
    suspend fun speakAndWait(text: String): Boolean = suspendCancellableCoroutine { continuation ->
        if (!isTtsReady) {
            Log.e(TAG, "Text-to-Speech no está inicializado correctamente")
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        
        val utteranceId = "speech_${System.currentTimeMillis()}"
        
        val listener = object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                _currentState.value = State.SPEAKING
            }
            
            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    textToSpeech.setOnUtteranceProgressListener(null)
                    _currentState.value = State.IDLE
                    continuation.resume(true)
                }
            }
            
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    textToSpeech.setOnUtteranceProgressListener(null)
                    _currentState.value = State.IDLE
                    Log.e(TAG, "Error en la síntesis de voz")
                    continuation.resume(false)
                }
            }
        }
        
        textToSpeech.setOnUtteranceProgressListener(listener)
        
        // Detener cualquier voz en curso
        textToSpeech.stop()
        
        // Hablar nuevo texto
        textToSpeech.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
        
        continuation.invokeOnCancellation {
            textToSpeech.stop()
            textToSpeech.setOnUtteranceProgressListener(null)
        }
    }
    
    /**
     * Inicia la escucha activa para un comando
     */
    suspend fun listenForCommand(): String? {
        _currentState.value = State.LISTENING
        
        try {
            val result = speechRecognizer.startListening()
            _currentState.value = State.IDLE
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error al escuchar comando", e)
            _currentState.value = State.IDLE
            return null
        }
    }
    
    /**
     * Inicia la detección de palabra clave en segundo plano
     */
    fun startBackgroundListening(onWakeWordDetected: () -> Unit) {
        wakeWordDetector.startDetection(onWakeWordDetected)
    }
    
    /**
     * Detiene la detección de palabra clave en segundo plano
     */
    fun stopBackgroundListening() {
        wakeWordDetector.stopDetection()
    }
    
    /**
     * Cambia la velocidad de habla
     * @param rate Velocidad de 0.5f a 2.0f (1.0f es normal)
     */
    fun setSpeechRate(rate: Float) {
        if (isTtsReady) {
            textToSpeech.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        }
    }
    
    /**
     * Cambia el tono de voz
     * @param pitch Tono de 0.5f a 2.0f (1.0f es normal)
     */
    fun setPitch(pitch: Float) {
        if (isTtsReady) {
            textToSpeech.setPitch(pitch.coerceIn(0.5f, 2.0f))
        }
    }
}