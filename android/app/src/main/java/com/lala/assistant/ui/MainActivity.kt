package com.lala.assistant.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lala.assistant.core.agent.AgentManager
import com.lala.assistant.core.voice.VoiceManager
import com.lala.assistant.services.AssistantService
import com.lala.assistant.ui.theme.LalaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Actividad principal de la aplicación
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // Referencia al servicio del asistente
    private var assistantService: AssistantService? = null
    private var voiceManager: VoiceManager? = null
    private var agentManager: AgentManager? = null
    private var bound = false
    
    // Estado para la interfaz de usuario
    private val currentCommand = mutableStateOf("")
    private val lastResponse = mutableStateOf("")
    private val isListening = mutableStateOf(false)
    
    // Conexión con el servicio
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Servicio conectado")
            
            val binder = service as AssistantService.LocalBinder
            assistantService = binder.getService()
            voiceManager = binder.getVoiceManager()
            agentManager = binder.getAgentManager()
            bound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Servicio desconectado")
            assistantService = null
            voiceManager = null
            agentManager = null
            bound = false
        }
    }
    
    // Lanzador para solicitar permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Verificar si todos los permisos fueron concedidos
        val allGranted = permissions.entries.all { it.value }
        
        if (allGranted) {
            Log.d(TAG, "Todos los permisos concedidos, iniciando servicio")
            startAndBindService()
        } else {
            Log.e(TAG, "No se concedieron todos los permisos")
            Toast.makeText(
                this,
                "Lala necesita permisos para funcionar correctamente",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Solicitar permisos necesarios
        requestPermissions()
        
        // Configurar la interfaz de usuario con Jetpack Compose
        setContent {
            LalaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    MainContent(
                        lastCommand = currentCommand.value,
                        lastResponse = lastResponse.value,
                        isListening = isListening.value,
                        onMicClick = { startListening() }
                    )
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        startAndBindService()
    }
    
    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
    
    /**
     * Solicita los permisos necesarios para la aplicación
     */
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
        )
        
        // Verificar qué permisos necesitan ser solicitados
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            // Todos los permisos ya concedidos
            startAndBindService()
        }
    }
    
    /**
     * Inicia y vincula al servicio del asistente
     */
    private fun startAndBindService() {
        // Iniciar servicio
        val serviceIntent = Intent(this, AssistantService::class.java)
        startForegroundService(serviceIntent)
        
        // Vincular al servicio
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * Inicia la escucha activa para un comando
     */
    private fun startListening() {
        if (voiceManager == null || !bound) {
            Toast.makeText(
                this,
                "Servicio no disponible. Espera un momento.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        isListening.value = true
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Escuchar comando
                val command = voiceManager?.listenForCommand()
                isListening.value = false
                
                if (!command.isNullOrBlank()) {
                    currentCommand.value = command
                    
                    // Procesar comando
                    val response = agentManager?.processCommand(command) ?: 
                        "No pude procesar tu comando."
                    
                    lastResponse.value = response
                    
                    // Responder por voz
                    voiceManager?.speak(response)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "No pude entender. Intenta de nuevo.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar comando", e)
                isListening.value = false
                
                Toast.makeText(
                    this@MainActivity,
                    "Error al procesar comando",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

/**
 * Componente principal de la interfaz
 */
@Composable
fun MainContent(
    lastCommand: String,
    lastResponse: String,
    isListening: Boolean,
    onMicClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Título
        Text(
            text = "Lala Assistant",
            style = MaterialTheme.typography.h4,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Información de estado
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = if (isListening) "Escuchando..." else "Listo para escuchar",
                    style = MaterialTheme.typography.h6
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (lastCommand.isNotEmpty()) {
                    Text(
                        text = "Tú: $lastCommand",
                        style = MaterialTheme.typography.body1
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (lastResponse.isNotEmpty()) {
                    Text(
                        text = "Lala: $lastResponse",
                        style = MaterialTheme.typography.body1
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Botón de micrófono
        Button(
            onClick = onMicClick,
            modifier = Modifier.size(64.dp),
            enabled = !isListening
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Micrófono",
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Toca el micrófono o di \"Hey Lala\"",
            style = MaterialTheme.typography.caption,
            textAlign = TextAlign.Center
        )
    }
}