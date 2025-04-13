package com.lala.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base de datos principal de la aplicación usando Room
 */
@Database(
    entities = [Memory::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    // DAOs
    abstract fun memoryDao(): MemoryDao
    
    companion object {
        // Singleton para prevenir múltiples instancias
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lala_database"
                )
                .fallbackToDestructiveMigration() // Recrear la BD si cambia la versión
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}