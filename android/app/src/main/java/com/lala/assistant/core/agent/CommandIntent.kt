package com.lala.assistant.core.agent

/**
 * Representa la intención detectada en un comando de usuario
 * 
 * @property type Tipo de intención (ej: GET_WEATHER, CREATE_REMINDER)
 * @property entities Mapa de entidades extraídas del comando
 */
data class CommandIntent(
    val type: String,
    val entities: Map<String, String>
)