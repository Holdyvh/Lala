package com.lala.assistant.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Servicio para interacciones con APIs remotas
 * 
 * Gestiona las llamadas a servicios en la nube cuando el dispositivo tiene
 * conectividad a Internet.
 */
class ApiService(private val context: Context) {
    
    companion object {
        private const val TAG = "ApiService"
        
        // Endpoints base (en un entorno real, estos estarían en buildConfig o properties)
        private const val WEATHER_API_URL = "https://api.openweathermap.org/data/2.5/weather"
        private const val NLP_API_URL = "https://api.openai.com/v1/completions"
        
        // Tiempo máximo en milisegundos para conectarse
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 15000
    }
    
    /**
     * Verifica si el dispositivo tiene conectividad a Internet
     */
    fun isNetworkAvailable(): Boolean {
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
    
    /**
     * Obtiene datos del clima para una ubicación específica
     */
    suspend fun getWeather(location: String, apiKey: String): WeatherData {
        return withContext(Dispatchers.IO) {
            try {
                if (!isNetworkAvailable()) {
                    Log.w(TAG, "No hay conexión a Internet para obtener el clima")
                    return@withContext WeatherData(
                        temperature = 0.0,
                        condition = "Desconocido",
                        location = location,
                        humidity = 0,
                        windSpeed = 0.0,
                        isSuccess = false,
                        errorMessage = "Sin conexión a Internet"
                    )
                }
                
                val url = URL("$WEATHER_API_URL?q=$location&units=metric&appid=$apiKey")
                val connection = url.openConnection() as HttpsURLConnection
                
                connection.apply {
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    requestMethod = "GET"
                }
                
                val responseCode = connection.responseCode
                
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    
                    // Extraer datos del JSON
                    val main = jsonObject.getJSONObject("main")
                    val weather = jsonObject.getJSONArray("weather").getJSONObject(0)
                    val wind = jsonObject.getJSONObject("wind")
                    
                    WeatherData(
                        temperature = main.getDouble("temp"),
                        condition = weather.getString("main"),
                        location = jsonObject.getString("name"),
                        humidity = main.getInt("humidity"),
                        windSpeed = wind.getDouble("speed"),
                        isSuccess = true,
                        errorMessage = null
                    )
                } else {
                    Log.e(TAG, "Error en la respuesta de la API del clima: $responseCode")
                    WeatherData(
                        temperature = 0.0,
                        condition = "Error",
                        location = location,
                        humidity = 0,
                        windSpeed = 0.0,
                        isSuccess = false,
                        errorMessage = "Error en la API: $responseCode"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener datos del clima", e)
                WeatherData(
                    temperature = 0.0,
                    condition = "Error",
                    location = location,
                    humidity = 0,
                    windSpeed = 0.0,
                    isSuccess = false,
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Realiza una consulta NLP utilizando la API de OpenAI
     */
    suspend fun performNlpQuery(prompt: String, apiKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!isNetworkAvailable()) {
                    Log.w(TAG, "No hay conexión a Internet para consulta NLP")
                    return@withContext "Lo siento, necesito conexión a Internet para responder eso."
                }
                
                val url = URL(NLP_API_URL)
                val connection = url.openConnection() as HttpsURLConnection
                
                val requestBody = JSONObject().apply {
                    put("model", "text-davinci-003")
                    put("prompt", prompt)
                    put("max_tokens", 150)
                    put("temperature", 0.7)
                }.toString()
                
                connection.apply {
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    doOutput = true
                }
                
                connection.outputStream.use { os ->
                    val input = requestBody.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }
                
                val responseCode = connection.responseCode
                
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val choices = jsonObject.getJSONArray("choices")
                    
                    if (choices.length() > 0) {
                        choices.getJSONObject(0).getString("text").trim()
                    } else {
                        "No pude generar una respuesta adecuada."
                    }
                } else {
                    Log.e(TAG, "Error en la respuesta de la API de NLP: $responseCode")
                    "Lo siento, hubo un problema con el servicio de procesamiento de lenguaje."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al realizar consulta NLP", e)
                "Lo siento, ocurrió un error al procesar tu consulta."
            }
        }
    }
    
    /**
     * Clase de datos para información del clima
     */
    data class WeatherData(
        val temperature: Double,
        val condition: String,
        val location: String,
        val humidity: Int,
        val windSpeed: Double,
        val isSuccess: Boolean,
        val errorMessage: String?
    )
}