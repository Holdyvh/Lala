package com.lala.assistant.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

/**
 * Convertidores de tipo para Room Database
 */
class Converters {
    
    private val gson = Gson()
    
    /**
     * Convierte lista de strings a string para almacenamiento
     */
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }
    
    /**
     * Convierte string almacenado a lista de strings
     */
    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }
    
    /**
     * Convierte Date a Long para almacenamiento
     */
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }
    
    /**
     * Convierte Long almacenado a Date
     */
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }
    
    /**
     * Convierte MemoryType a String para almacenamiento
     */
    @TypeConverter
    fun fromMemoryType(type: MemoryType): String {
        return type.name
    }
    
    /**
     * Convierte String almacenado a MemoryType
     */
    @TypeConverter
    fun toMemoryType(value: String): MemoryType {
        return try {
            MemoryType.valueOf(value)
        } catch (e: Exception) {
            MemoryType.FACT // Valor por defecto
        }
    }
}