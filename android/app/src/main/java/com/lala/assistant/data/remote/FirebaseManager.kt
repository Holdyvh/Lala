package com.lala.assistant.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Gestiona las interacciones con Firebase (autenticación, Firestore, etc.)
 */
class FirebaseManager(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseManager"
        
        // Colecciones de Firestore
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_COMMANDS = "command_history"
    }
    
    // Instancias Firebase
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
    
    // Usuario actual
    val currentUser: FirebaseUser?
        get() = auth.currentUser
    
    /**
     * Inicia sesión con email y contraseña
     */
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            authResult.user?.let { 
                Result.success(it)
            } ?: Result.failure(Exception("Error desconocido al iniciar sesión"))
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar sesión", e)
            Result.failure(e)
        }
    }
    
    /**
     * Registra un nuevo usuario
     */
    suspend fun signUp(email: String, password: String, username: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            
            authResult.user?.let { user ->
                // Crear documento del usuario en Firestore
                val userData = hashMapOf(
                    "username" to username,
                    "email" to email,
                    "created_at" to Date(),
                    "wake_word" to "Lala",
                    "settings" to hashMapOf(
                        "voice_activation" to true,
                        "prefer_offline" to false,
                        "theme" to "system"
                    )
                )
                
                // Guardar datos del usuario
                firestore.collection(COLLECTION_USERS)
                    .document(user.uid)
                    .set(userData)
                    .await()
                
                Result.success(user)
            } ?: Result.failure(Exception("Error desconocido al registrar usuario"))
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar usuario", e)
            Result.failure(e)
        }
    }
    
    /**
     * Cierra la sesión del usuario actual
     */
    fun signOut() {
        auth.signOut()
    }
    
    /**
     * Registra un comando y su respuesta en el historial
     */
    suspend fun saveCommandToHistory(command: String, response: String, mode: String): Result<String> {
        return try {
            val userId = currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))
            
            val commandData = hashMapOf(
                "user_id" to userId,
                "command" to command,
                "response" to response,
                "mode" to mode,
                "timestamp" to Date()
            )
            
            val docRef = firestore.collection(COLLECTION_COMMANDS)
                .add(commandData)
                .await()
            
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar comando en historial", e)
            Result.failure(e)
        }
    }
    
    /**
     * Actualiza la palabra clave del usuario
     */
    suspend fun updateWakeWord(wakeWord: String): Result<Unit> {
        return try {
            val userId = currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))
            
            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .update("wake_word", wakeWord)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar palabra clave", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene el token FCM para notificaciones
     */
    suspend fun getFcmToken(): Result<String> {
        return try {
            val token = messaging.token.await()
            Result.success(token)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener token FCM", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sincroniza datos locales con Firestore
     */
    suspend fun syncLocalData(dataType: String, data: List<Map<String, Any>>): Result<Unit> {
        return try {
            val userId = currentUser?.uid ?: return Result.failure(Exception("Usuario no autenticado"))
            
            // Crear referencia a la colección del usuario
            val userDataRef = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(dataType)
            
            // Usar lote para operaciones múltiples
            val batch = firestore.batch()
            
            // Agregar cada ítem al lote
            data.forEach { item ->
                val docRef = if (item.containsKey("id") && item["id"] != null) {
                    userDataRef.document(item["id"].toString())
                } else {
                    userDataRef.document()
                }
                
                batch.set(docRef, item)
            }
            
            // Ejecutar el lote
            batch.commit().await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al sincronizar datos locales", e)
            Result.failure(e)
        }
    }
}