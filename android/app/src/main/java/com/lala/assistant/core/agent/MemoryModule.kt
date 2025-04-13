package com.lala.assistant.core.agent

import android.util.Log
import com.lala.assistant.data.local.Memory
import com.lala.assistant.data.local.MemoryDao
import com.lala.assistant.data.local.MemoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Date

/**
 * Gestiona la memoria del asistente, proporcionando acceso a información pasada
 * y contexto para mejorar la interpretación de comandos actuales.
 */
class MemoryModule(private val memoryDao: MemoryDao) {

    companion object {
        private const val TAG = "MemoryModule"
    }

    /**
     * Almacena una interacción de comando-respuesta en la memoria
     */
    suspend fun storeInteraction(command: String, response: String) {
        try {
            val memory = Memory(
                type = MemoryType.CONVERSATION,
                content = "Usuario: $command\nLala: $response",
                tags = extractTags(command),
                importance = calculateImportance(command),
                createdAt = Date()
            )
            memoryDao.insert(memory)
            Log.d(TAG, "Interacción almacenada: ${memory.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al almacenar interacción", e)
        }
    }

    /**
     * Recupera memorias relevantes al contexto actual
     */
    suspend fun getRelevantMemories(currentInput: String): List<String> {
        return try {
            // Por ahora hacemos una búsqueda simple por palabras clave
            // TODO: Mejorar con embeddings para búsqueda semántica
            val keywords = extractKeywords(currentInput)
            
            val memories = mutableListOf<Memory>()
            
            // Buscar por cada palabra clave
            for (keyword in keywords) {
                val results = memoryDao.searchMemories("%$keyword%").first()
                memories.addAll(results)
            }
            
            // Filtrar duplicados y ordenar por fecha (más reciente primero)
            memories.distinctBy { it.id }
                .sortedByDescending { it.createdAt }
                .take(5) // Limitamos a 5 memorias para no sobrecargar
                .map { it.content }
        } catch (e: Exception) {
            Log.e(TAG, "Error al recuperar memorias relevantes", e)
            emptyList()
        }
    }

    /**
     * Almacena un hecho o información general en la memoria
     */
    suspend fun storeFact(fact: String, tags: List<String> = emptyList()) {
        try {
            val memory = Memory(
                type = MemoryType.FACT,
                content = fact,
                tags = tags,
                importance = 70, // Los hechos suelen ser importantes
                createdAt = Date()
            )
            memoryDao.insert(memory)
        } catch (e: Exception) {
            Log.e(TAG, "Error al almacenar hecho", e)
        }
    }

    /**
     * Almacena una preferencia del usuario
     */
    suspend fun storePreference(preference: String) {
        try {
            val memory = Memory(
                type = MemoryType.PREFERENCE,
                content = preference,
                tags = listOf("preferencia", "configuración"),
                importance = 80, // Las preferencias son muy importantes
                createdAt = Date()
            )
            memoryDao.insert(memory)
        } catch (e: Exception) {
            Log.e(TAG, "Error al almacenar preferencia", e)
        }
    }

    /**
     * Elimina memorias caducadas (tareas completadas, memorias temporales, etc.)
     */
    suspend fun cleanupExpiredMemories() {
        try {
            memoryDao.deleteExpiredMemories()
            Log.d(TAG, "Limpieza de memorias caducadas completada")
        } catch (e: Exception) {
            Log.e(TAG, "Error al limpiar memorias caducadas", e)
        }
    }

    /**
     * Extrae palabras clave de un texto para búsqueda
     */
    private fun extractKeywords(text: String): List<String> {
        // Lista de palabras "stop" (comunes que no aportan significado de búsqueda)
        val stopWords = setOf("el", "la", "los", "las", "un", "una", "unos", "unas", 
                             "y", "o", "de", "a", "en", "que", "por", "como", "para")
        
        return text.toLowerCase()
            .replace("[^a-záéíóúüñ\\s]".toRegex(), " ") // Eliminar caracteres no alfabéticos
            .split("\\s+".toRegex()) // Dividir por espacios
            .filter { it.length > 3 } // Palabras mayores a 3 caracteres
            .filter { it !in stopWords } // Remover stop words
            .distinct() // Eliminar duplicados
            .take(5) // Máximo 5 keywords para la búsqueda
    }

    /**
     * Extrae etiquetas para clasificar la memoria
     */
    private fun extractTags(text: String): List<String> {
        val tags = mutableListOf<String>()
        
        // Detectar categorías basadas en palabras clave
        val lowercaseText = text.toLowerCase()
        
        if (lowercaseText.contains("clima") || lowercaseText.contains("temperatura") || 
            lowercaseText.contains("lluvia")) {
            tags.add("clima")
        }
        
        if (lowercaseText.contains("recuérdame") || lowercaseText.contains("recordatorio") || 
            lowercaseText.contains("alarma")) {
            tags.add("recordatorio")
        }
        
        if (lowercaseText.contains("nota") || lowercaseText.contains("apunta") || 
            lowercaseText.contains("guarda")) {
            tags.add("nota")
        }
        
        if (lowercaseText.contains("hora") || lowercaseText.contains("fecha") || 
            lowercaseText.contains("día")) {
            tags.add("tiempo")
        }
        
        // Agregar una etiqueta genérica
        tags.add("interacción")
        
        return tags
    }

    /**
     * Calcula un puntaje de importancia para la memoria basado en contenido
     */
    private fun calculateImportance(text: String): Int {
        // Palabras que indican mayor importancia
        val importantIndicators = listOf("importante", "urgente", "crítico", "esencial", 
                                        "necesito", "recordar", "no olvidar", "clave")
        
        val lowercaseText = text.toLowerCase()
        
        // Nivel base de importancia
        var importance = 50
        
        // Incrementar si contiene indicadores de importancia
        for (indicator in importantIndicators) {
            if (lowercaseText.contains(indicator)) {
                importance += 10
            }
        }
        
        // Limitar a rango 0-100
        return importance.coerceIn(0, 100)
    }
}