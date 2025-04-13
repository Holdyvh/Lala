package com.lala.assistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverter
import java.util.Date

/**
 * Tipos de memoria para el asistente
 */
enum class MemoryType {
    FACT,           // Hechos generales o información objetiva
    CONVERSATION,   // Fragmentos de conversaciones pasadas
    PREFERENCE,     // Preferencias del usuario
    TASK,           // Tareas pendientes o completadas
    SKILL,          // Habilidades aprendidas o adquiridas
    TEMPORAL        // Memoria a corto plazo (se eliminará automáticamente)
}

/**
 * Clase de entidad para almacenar la memoria del asistente
 */
@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "type")
    val type: MemoryType,
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "tags")
    val tags: List<String> = emptyList(),
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),
    
    @ColumnInfo(name = "expires_at")
    val expiresAt: Date? = null,
    
    @ColumnInfo(name = "importance")
    val importance: Int = 0,    // 0-100, donde 100 es máxima importancia
    
    @ColumnInfo(name = "last_accessed")
    val lastAccessed: Date? = null,
    
    @ColumnInfo(name = "access_count")
    val accessCount: Int = 0
)