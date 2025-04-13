package com.lala.assistant.data.remote

import android.content.Context
import android.util.Log
import com.lala.assistant.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Servicio para reconocimiento de voz en línea utilizando servicios como:
 * - Google Speech-to-Text
 * - OpenAI Whisper
 * - Azure Speech
 * 
 * Se selecciona el servicio más apropiado según la disponibilidad y configuración.
 */
class OnlineSpeechRecognitionService(
    private val context: Context
) {
    companion object {
        private const val TAG = "OnlineASR"
        
        // Tiempos de espera para conexiones
        private const val CONNECTION_TIMEOUT = 10L
        private const val READ_TIMEOUT = 30L
        private const val WRITE_TIMEOUT = 15L
        
        // URLs para diferentes servicios
        private const val WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val GOOGLE_ASR_URL = "https://speech.googleapis.com/v1/speech:recognize"
    }
    
    // Cliente HTTP para peticiones
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Transcribe audio a texto usando el servicio más apropiado
     * 
     * @param audioFile Archivo de audio a transcribir (formato: WAV o MP3)
     * @param preferredService Servicio preferido (whisper, google, azure)
     * @param language Código de idioma ISO (es-ES, en-US, etc.)
     * @return Texto transcrito y metadatos
     */
    suspend fun transcribeAudio(
        audioFile: File,
        preferredService: String = "whisper",
        language: String = "es-ES"
    ): TranscriptionResult {
        return when (preferredService) {
            "whisper" -> transcribeWithWhisper(audioFile, language)
            "google" -> transcribeWithGoogle(audioFile, language)
            else -> {
                // Por defecto usar Whisper si el servicio especificado no está implementado
                Log.w(TAG, "Servicio $preferredService no implementado, usando Whisper")
                transcribeWithWhisper(audioFile, language)
            }
        }
    }
    
    /**
     * Transcribe audio usando OpenAI Whisper API
     */
    private suspend fun transcribeWithWhisper(
        audioFile: File,
        language: String
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando transcripción con Whisper")
            
            // Crear cuerpo multipart para la petición
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
                )
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("language", language.split("-")[0]) // Whisper usa solo el código de idioma principal
                .addFormDataPart("response_format", "json")
                .build()
            
            // TODO: Obtener API key real
            val apiKey = "OPENAI_API_KEY" // Se debe reemplazar por un API key real, preferiblemente desde un lugar seguro
            
            // Crear petición
            val request = Request.Builder()
                .url(WHISPER_API_URL)
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()
            
            // Realizar petición síncronamente
            return@withContext suspendCancellableCoroutine { continuation ->
                try {
                    // Ejecutar petición
                    val response = client.newCall(request).execute()
                    
                    // Procesar respuesta
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val json = JSONObject(responseBody)
                        val text = json.getString("text")
                        
                        Log.d(TAG, "Transcripción exitosa: $text")
                        continuation.resume(
                            TranscriptionResult(
                                text = text,
                                confidence = 0.9f, // Whisper no proporciona confianza
                                service = "whisper"
                            )
                        )
                    } else {
                        val errorMsg = "Error en Whisper API: ${response.code}"
                        Log.e(TAG, errorMsg)
                        continuation.resumeWithException(Exception(errorMsg))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Excepción en transcripción Whisper", e)
                    continuation.resumeWithException(e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en transcripción Whisper", e)
            throw e
        }
    }
    
    /**
     * Transcribe audio usando Google Speech-to-Text API
     */
    private suspend fun transcribeWithGoogle(
        audioFile: File,
        language: String
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando transcripción con Google Speech")
            
            // Leer bytes del archivo
            val audioBytes = audioFile.readBytes()
            val base64Audio = android.util.Base64.encodeToString(
                audioBytes,
                android.util.Base64.NO_WRAP
            )
            
            // Crear cuerpo JSON para la petición
            val jsonBody = JSONObject().apply {
                put("config", JSONObject().apply {
                    put("languageCode", language)
                    put("model", "default")
                    put("enableAutomaticPunctuation", true)
                })
                put("audio", JSONObject().apply {
                    put("content", base64Audio)
                })
            }
            
            // TODO: Obtener API key real
            val apiKey = "GOOGLE_API_KEY" // Se debe reemplazar por un API key real
            
            // Crear cuerpo de petición
            val requestBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                jsonBody.toString()
            )
            
            // Crear petición
            val request = Request.Builder()
                .url("$GOOGLE_ASR_URL?key=$apiKey")
                .post(requestBody)
                .build()
            
            // Realizar petición síncronamente
            return@withContext suspendCancellableCoroutine { continuation ->
                try {
                    // Ejecutar petición
                    val response = client.newCall(request).execute()
                    
                    // Procesar respuesta
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val json = JSONObject(responseBody)
                        val results = json.getJSONArray("results")
                        
                        var transcription = ""
                        var confidence = 0f
                        
                        if (results.length() > 0) {
                            val result = results.getJSONObject(0)
                            val alternatives = result.getJSONArray("alternatives")
                            
                            if (alternatives.length() > 0) {
                                val alternative = alternatives.getJSONObject(0)
                                transcription = alternative.getString("transcript")
                                confidence = alternative.optDouble("confidence", 0.0).toFloat()
                            }
                        }
                        
                        Log.d(TAG, "Transcripción exitosa: $transcription")
                        continuation.resume(
                            TranscriptionResult(
                                text = transcription,
                                confidence = confidence,
                                service = "google"
                            )
                        )
                    } else {
                        val errorMsg = "Error en Google API: ${response.code}"
                        Log.e(TAG, errorMsg)
                        continuation.resumeWithException(Exception(errorMsg))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Excepción en transcripción Google", e)
                    continuation.resumeWithException(e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en transcripción Google", e)
            throw e
        }
    }
}

/**
 * Clase para representar el resultado de una transcripción
 */
data class TranscriptionResult(
    val text: String,
    val confidence: Float,
    val service: String,
    val alternatives: List<String> = emptyList(),
    val error: String? = null
)