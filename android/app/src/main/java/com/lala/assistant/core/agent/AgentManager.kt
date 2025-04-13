package com.lala.assistant.core.agent

import android.content.Context
import android.util.Log
import com.lala.assistant.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AgentManager: Componente central para gestionar la inteligencia de Lala
 * 
 * Coordina el procesamiento de comandos, planificación de tareas y
 * acceso a la memoria para proporcionar respuestas coherentes.
 */
class AgentManager(
    private val taskPlanner: TaskPlanner,
    private val memoryModule: MemoryModule,
    private val apiService: ApiService,
    private val context: Context
) {
    companion object {
        private const val TAG = "AgentManager"
    }
    
    /**
     * Procesa un comando de texto y devuelve una respuesta
     */
    suspend fun processCommand(command: String): String {
        return withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "Procesando comando: $command")
                
                // 1. Consultar memoria para contexto
                val context = memoryModule.getRelevantMemories(command)
                
                // 2. Análisis de intención y entidades
                val intent = analyzeIntent(command, context)
                
                // 3. Planificar acciones necesarias
                val plan = taskPlanner.createPlan(intent)
                
                // 4. Ejecutar plan
                val response = executeActions(plan)
                
                // 5. Almacenar interacción en memoria
                memoryModule.storeInteraction(command, response)
                
                response
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar comando", e)
                "Lo siento, ocurrió un error al procesar tu solicitud."
            }
        }
    }
    
    /**
     * Analiza la intención del usuario y extrae entidades
     */
    private suspend fun analyzeIntent(command: String, context: List<String>): CommandIntent {
        // TODO: Implementar análisis real de intención con NLP
        // Por ahora usamos un análisis simple basado en palabras clave
        
        val lowercaseCommand = command.toLowerCase()
        
        return when {
            lowercaseCommand.contains("hora") -> 
                CommandIntent("GET_TIME", emptyMap())
                
            lowercaseCommand.contains("clima") || lowercaseCommand.contains("tiempo") -> 
                CommandIntent("GET_WEATHER", emptyMap())
                
            lowercaseCommand.contains("recuérdame") || lowercaseCommand.contains("recordatorio") -> {
                val reminder = extractReminder(lowercaseCommand)
                CommandIntent("CREATE_REMINDER", mapOf("text" to reminder))
            }
                
            lowercaseCommand.contains("nota") || lowercaseCommand.contains("anota") -> {
                val note = extractNote(lowercaseCommand)
                CommandIntent("CREATE_NOTE", mapOf("text" to note))
            }
                
            lowercaseCommand.contains("chiste") -> 
                CommandIntent("TELL_JOKE", emptyMap())
                
            else -> CommandIntent("UNKNOWN", emptyMap())
        }
    }
    
    /**
     * Extrae el texto del recordatorio del comando
     */
    private fun extractReminder(command: String): String {
        // Buscar después de palabras clave comunes
        val patterns = listOf("recuérdame que ", "recordatorio ", "recuérdame ")
        for (pattern in patterns) {
            val index = command.indexOf(pattern)
            if (index >= 0) {
                return command.substring(index + pattern.length)
            }
        }
        return command
    }
    
    /**
     * Extrae el texto de la nota del comando
     */
    private fun extractNote(command: String): String {
        // Buscar después de palabras clave comunes
        val patterns = listOf("toma nota de ", "anota ", "escribe ", "nota ")
        for (pattern in patterns) {
            val index = command.indexOf(pattern)
            if (index >= 0) {
                return command.substring(index + pattern.length)
            }
        }
        return command
    }
    
    /**
     * Ejecuta las acciones del plan y genera una respuesta
     */
    private suspend fun executeActions(plan: ActionPlan): String {
        // TODO: Implementar ejecución real de acciones
        // Por ahora simulamos respuestas para fines de demostración
        
        when (plan.intent.type) {
            "GET_TIME" -> {
                val time = java.text.SimpleDateFormat("HH:mm").format(java.util.Date())
                return "Son las $time."
            }
            "GET_WEATHER" -> {
                return "Actualmente está parcialmente nublado con una temperatura de 24 grados."
            }
            "CREATE_REMINDER" -> {
                val text = plan.intent.entities["text"] ?: "recordatorio"
                return "He creado un recordatorio para: $text"
            }
            "CREATE_NOTE" -> {
                val text = plan.intent.entities["text"] ?: "nota"
                return "He guardado la nota: $text"
            }
            "TELL_JOKE" -> {
                val jokes = listOf(
                    "¿Por qué los pájaros no usan Facebook? Porque ya tienen Twitter.",
                    "¿Qué hace una abeja en el gimnasio? Zumba.",
                    "¿Cómo se llama un boomerang que no vuelve? Palo."
                )
                return jokes.random()
            }
            else -> {
                return "No estoy seguro de cómo ayudarte con eso. ¿Puedes ser más específico?"
            }
        }
    }
}