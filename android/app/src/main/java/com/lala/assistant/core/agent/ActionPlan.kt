package com.lala.assistant.core.agent

/**
 * Representa un plan de acción para responder a una intención del usuario
 * 
 * @property intent La intención detectada
 * @property actions Lista de acciones a ejecutar para cumplir con la intención
 * @property priority Prioridad del plan (0-10)
 */
data class ActionPlan(
    val intent: CommandIntent,
    val actions: List<Action> = emptyList(),
    val priority: Int = 5
)

/**
 * Representa una acción individual a ejecutar como parte de un plan
 * 
 * @property type Tipo de acción (e.g., API_CALL, DB_OPERATION)
 * @property parameters Parámetros necesarios para la acción
 * @property description Descripción legible de la acción
 */
data class Action(
    val type: String,
    val parameters: Map<String, Any> = emptyMap(),
    val description: String = ""
)