package com.lala.assistant.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * DAO para acceso a memoria en la base de datos
 */
@Dao
interface MemoryDao {
    
    /**
     * Inserta una nueva memoria
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: Memory): Long
    
    /**
     * Inserta múltiples memorias
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<Memory>): List<Long>
    
    /**
     * Actualiza una memoria existente
     */
    @Update
    suspend fun update(memory: Memory)
    
    /**
     * Elimina una memoria
     */
    @Delete
    suspend fun delete(memory: Memory)
    
    /**
     * Obtiene todas las memorias
     */
    @Query("SELECT * FROM memories")
    fun getAllMemories(): Flow<List<Memory>>
    
    /**
     * Obtiene una memoria por su ID
     */
    @Query("SELECT * FROM memories WHERE id = :memoryId")
    suspend fun getMemoryById(memoryId: Long): Memory?
    
    /**
     * Obtiene memorias por tipo
     */
    @Query("SELECT * FROM memories WHERE type = :type")
    fun getMemoriesByType(type: MemoryType): Flow<List<Memory>>
    
    /**
     * Busca memorias por contenido (texto libre)
     */
    @Query("SELECT * FROM memories WHERE content LIKE '%' || :searchQuery || '%'")
    fun searchMemories(searchQuery: String): Flow<List<Memory>>
    
    /**
     * Busca memorias por etiquetas específicas
     */
    @Query("SELECT * FROM memories WHERE tags LIKE '%' || :tag || '%'")
    fun getMemoriesByTag(tag: String): Flow<List<Memory>>
    
    /**
     * Actualiza el contador de accesos y la fecha del último acceso
     */
    @Query("UPDATE memories SET access_count = access_count + 1, last_accessed = :timestamp WHERE id = :memoryId")
    suspend fun updateAccessStats(memoryId: Long, timestamp: Date = Date())
    
    /**
     * Elimina memorias caducadas
     */
    @Query("DELETE FROM memories WHERE expires_at IS NOT NULL AND expires_at < :currentTime")
    suspend fun deleteExpiredMemories(currentTime: Date = Date())
}