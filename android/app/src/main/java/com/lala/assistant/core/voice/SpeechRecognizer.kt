package com.lala.assistant.core.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import kotlin.coroutines.resume

/**
 * Gestiona el reconocimiento de voz con soporte para modos offline y online
 */
class SpeechRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "SpeechRecognizer"
        private const val TIMEOUT_MS = 10000L // 10 segundos
    }
    
    // Servicio de reconocimiento Vosk (offline)
    private var voskSpeechService: SpeechService? = null
    private var voskModel: Model? = null
    
    // Opciones de configuración
    private var preferOffline = false
    private var modelPath = ""
    
    /**
     * Inicializa el reconocedor de voz
     */
    fun initialize() {
        // Intentar cargar modelo Vosk
        try {
            loadVoskModel()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar modelo Vosk", e)
        }
    }
    
    /**
     * Carga el modelo Vosk para reconocimiento offline
     */
    private fun loadVoskModel() {
        val appDir = context.getExternalFilesDir(null)
        val modelDir = File(appDir, "vosk-model-small-es")
        
        if (modelDir.exists()) {
            try {
                Log.d(TAG, "Cargando modelo Vosk: ${modelDir.absolutePath}")
                voskModel = Model(modelDir.absolutePath)
                modelPath = modelDir.absolutePath
                Log.d(TAG, "Modelo Vosk cargado correctamente")
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar modelo Vosk", e)
                voskModel = null
            }
        } else {
            Log.w(TAG, "Modelo Vosk no encontrado en: ${modelDir.absolutePath}")
        }
    }
    
    /**
     * Configura preferencia para modo offline o no
     */
    fun setPreferOffline(prefer: Boolean) {
        preferOffline = prefer
    }
    
    /**
     * Inicia escucha para reconocimiento de voz
     */
    suspend fun startListening(): String? = withTimeout(TIMEOUT_MS) {
        return@withTimeout if (isNetworkAvailable() && !preferOffline) {
            startOnlineRecognition()
        } else {
            startOfflineRecognition()
        }
    }
    
    /**
     * Inicia reconocimiento de voz online (Google, Whisper, etc.)
     */
    private suspend fun startOnlineRecognition(): String? {
        // TODO: Implementar reconocimiento online con Google Speech o Whisper API
        
        // Por ahora simulamos un reconocimiento
        Log.d(TAG, "Utilizando reconocimiento online")
        return "Este es un resultado de reconocimiento online simulado"
    }
    
    /**
     * Inicia reconocimiento de voz offline con Vosk
     */
    private suspend fun startOfflineRecognition(): String? = suspendCancellableCoroutine { continuation ->
        if (voskModel == null) {
            Log.e(TAG, "Modelo Vosk no inicializado")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        
        try {
            Log.d(TAG, "Iniciando reconocimiento offline con Vosk")
            
            val recognizer = Recognizer(voskModel, 16000.0f)
            
            // Listener para eventos de reconocimiento
            val listener = object : RecognitionListener {
                var finalResult: String? = null
                
                override fun onPartialResult(hypothesis: String?) {
                    // Resultados parciales mientras habla
                    try {
                        if (hypothesis != null) {
                            val json = JSONObject(hypothesis)
                            val partial = json.optString("partial", "")
                            Log.d(TAG, "Resultado parcial: $partial")
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error al procesar resultado parcial", e)
                    }
                }
                
                override fun onResult(hypothesis: String?) {
                    // Resultado final
                    try {
                        if (hypothesis != null) {
                            val json = JSONObject(hypothesis)
                            val text = json.optString("text", "")
                            if (text.isNotEmpty()) {
                                finalResult = text
                                Log.d(TAG, "Resultado final: $text")
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error al procesar resultado final", e)
                    }
                }
                
                override fun onFinalResult(hypothesis: String?) {
                    // Resultado cuando termina de hablar y se procesa todo
                    try {
                        if (hypothesis != null) {
                            val json = JSONObject(hypothesis)
                            val text = json.optString("text", "")
                            if (text.isNotEmpty()) {
                                finalResult = text
                            }
                        }
                        
                        // Limpiar recursos y devolver resultado
                        voskSpeechService?.stop()
                        voskSpeechService = null
                        recognizer.close()
                        
                        continuation.resume(finalResult)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar resultado final", e)
                        continuation.resume(null)
                    }
                }
                
                override fun onError(exception: Exception?) {
                    Log.e(TAG, "Error en reconocimiento de voz", exception)
                    voskSpeechService?.stop()
                    voskSpeechService = null
                    recognizer.close()
                    continuation.resume(null)
                }
                
                override fun onTimeout() {
                    Log.d(TAG, "Tiempo agotado en reconocimiento de voz")
                    voskSpeechService?.stop()
                    voskSpeechService = null
                    recognizer.close()
                    continuation.resume(finalResult)
                }
            }
            
            // Iniciar servicio de reconocimiento
            voskSpeechService = SpeechService(recognizer, 16000.0f)
            voskSpeechService?.startListening(listener)
            
            continuation.invokeOnCancellation {
                voskSpeechService?.stop()
                voskSpeechService = null
                recognizer.close()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar reconocimiento offline", e)
            continuation.resume(null)
        }
    }
    
    /**
     * Detiene cualquier escucha activa
     */
    fun stopListening() {
        voskSpeechService?.stop()
        voskSpeechService = null
    }
    
    /**
     * Libera recursos
     */
    fun shutdown() {
        voskSpeechService?.shutdown()
        voskModel?.close()
    }
    
    /**
     * Verifica si el dispositivo tiene conexión a Internet
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
        
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}