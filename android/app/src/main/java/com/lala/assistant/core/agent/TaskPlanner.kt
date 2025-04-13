package com.lala.assistant.core.agent

import android.util.Log

/**
 * Responsable de crear planes de acción basados en las intenciones del usuario
 */
class TaskPlanner {
    
    companion object {
        private const val TAG = "TaskPlanner"
    }
    
    /**
     * Crea un plan de acción basado en una intención
     */
    fun createPlan(intent: CommandIntent): ActionPlan {
        Log.d(TAG, "Creando plan para intención: ${intent.type}")
        
        return when (intent.type) {
            "GET_TIME" -> createGetTimePlan(intent)
            "GET_WEATHER" -> createGetWeatherPlan(intent)
            "CREATE_REMINDER" -> createReminderPlan(intent)
            "CREATE_NOTE" -> createNotePlan(intent)
            "TELL_JOKE" -> createJokePlan(intent)
            else -> createDefaultPlan(intent)
        }
    }
    
    /**
     * Plan para obtener la hora actual
     */
    private fun createGetTimePlan(intent: CommandIntent): ActionPlan {
        val action = Action(
            type = "SYSTEM_OPERATION",
            parameters = mapOf("operation" to "getTime"),
            description = "Obtener la hora actual del sistema"
        )
        
        return ActionPlan(
            intent = intent,
            actions = listOf(action),
            priority = 3 // Prioridad baja, operación simple
        )
    }
    
    /**
     * Plan para obtener el clima
     */
    private fun createGetWeatherPlan(intent: CommandIntent): ActionPlan {
        // Determinar si tenemos una ubicación específica o usamos la predeterminada
        val location = intent.entities["location"] ?: "current"
        
        val checkNetworkAction = Action(
            type = "SYSTEM_OPERATION",
            parameters = mapOf("operation" to "checkNetwork"),
            description = "Verificar conectividad de red"
        )
        
        val getWeatherAction = Action(
            type = "API_CALL",
            parameters = mapOf("endpoint" to "weather", "location" to location),
            description = "Obtener datos del clima para $location"
        )
        
        val fallbackAction = Action(
            type = "SYSTEM_OPERATION",
            parameters = mapOf("operation" to "getCachedWeather", "location" to location),
            description = "Obtener datos de clima en caché para $location"
        )
        
        return ActionPlan(
            intent = intent,
            actions = listOf(checkNetworkAction, getWeatherAction, fallbackAction),
            priority = 5 // Prioridad media, requiere red potencialmente
        )
    }
    
    /**
     * Plan para crear un recordatorio
     */
    private fun createReminderPlan(intent: CommandIntent): ActionPlan {
        val reminderText = intent.entities["text"] ?: "Recordatorio sin texto"
        
        val parseTimeAction = Action(
            type = "NLP_OPERATION",
            parameters = mapOf("operation" to "extractTime", "text" to reminderText),
            description = "Extraer información de tiempo del recordatorio"
        )
        
        val saveReminderAction = Action(
            type = "DB_OPERATION",
            parameters = mapOf("operation" to "saveReminder", "text" to reminderText),
            description = "Guardar recordatorio en base de datos"
        )
        
        val scheduleAction = Action(
            type = "SYSTEM_OPERATION",
            parameters = mapOf("operation" to "scheduleNotification", "reminder" to reminderText),
            description = "Programar notificación para el recordatorio"
        )
        
        return ActionPlan(
            intent = intent,
            actions = listOf(parseTimeAction, saveReminderAction, scheduleAction),
            priority = 7 // Prioridad alta, creación de contenido
        )
    }
    
    /**
     * Plan para crear una nota
     */
    private fun createNotePlan(intent: CommandIntent): ActionPlan {
        val noteText = intent.entities["text"] ?: "Nota sin texto"
        
        val saveNoteAction = Action(
            type = "DB_OPERATION",
            parameters = mapOf("operation" to "saveNote", "text" to noteText),
            description = "Guardar nota en base de datos"
        )
        
        return ActionPlan(
            intent = intent,
            actions = listOf(saveNoteAction),
            priority = 6 // Prioridad media-alta, creación de contenido
        )
    }
    
    /**
     * Plan para contar un chiste
     */
    private fun createJokePlan(intent: CommandIntent): ActionPlan {
        val action = Action(
            type = "CONTENT_RETRIEVAL",
            parameters = mapOf("type" to "joke", "category" to "general"),
            description = "Obtener un chiste aleatorio"
        )
        
        return ActionPlan(
            intent = intent,
            actions = listOf(action),
            priority = 4 // Prioridad media-baja, entretenimiento
        )
    }
    
    /**
     * Plan por defecto para intenciones desconocidas
     */
    private fun createDefaultPlan(intent: CommandIntent): ActionPlan {
        val action = Action(
            type = "DEFAULT_RESPONSE",
            description = "Respuesta genérica para intención desconocida"
        )
        
        return ActionPlan(
            intent = intent,
            actions = listOf(action),
            priority = 2 // Prioridad muy baja
        )
    }
}