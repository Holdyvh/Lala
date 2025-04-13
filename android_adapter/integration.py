"""
Integración de los componentes de Lala para Android.

Este módulo integra todos los componentes de Lala (agente planner, servicios AI, etc.)
para crear una API coherente que puede utilizarse desde una aplicación Android.
"""

import os
import sys
import json
import logging
import threading
import time
from typing import Dict, Any, Optional, List, Callable, Tuple, Union

# Importar componentes de Lala
from .core import AndroidAdapter, get_android_adapter
from services.agent_planner import agent_planner, process_agent_command
from services.ai_models_router import ai_router, process_ai_request
from services.nlp import process_command
from services.model_optimizer import optimizer, get_optimal_models_config
from utils import is_online

# Configuración de logging
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)


class LalaAssistant:
    """
    Asistente principal de Lala para integración con Android.
    
    Esta clase coordina todos los componentes del asistente y proporciona
    una API coherente para ser utilizada desde una aplicación Android.
    """
    
    def __init__(self, 
               user_id: Optional[int] = None,
               prefer_offline: bool = False,
               wake_word: str = "Lala"):
        """
        Inicializa el asistente Lala.
        
        Args:
            user_id: ID del usuario actual (opcional)
            prefer_offline: Si es True, prioriza procesamiento offline
            wake_word: Palabra de activación para comandos de voz
        """
        logger.info("Inicializando asistente Lala para Android")
        
        self.user_id = user_id
        self.prefer_offline = prefer_offline
        self.wake_word = wake_word
        
        # Inicializar adaptador Android
        self.android = get_android_adapter()
        
        # Estado del asistente
        self.is_active = False
        self.is_processing = False
        self.last_command = None
        self.last_response = None
        
        # Configurar modo offline según preferencia
        ai_router.offline_mode = prefer_offline
        
        # Thread para escucha en segundo plano
        self.background_thread = None
        self.should_stop_background = False
        
        logger.info("Asistente Lala inicializado correctamente")
    
    def start(self) -> bool:
        """
        Inicia el asistente y sus servicios.
        
        Returns:
            bool: True si se inició correctamente
        """
        logger.info("Iniciando asistente Lala")
        
        # Iniciar servicios en Android
        self.android.bridge.vibrate(200)  # Vibración corta
        self.android.notifications.show_notification(
            title="Lala está activa",
            message="Di 'Lala' para activar el asistente",
            notification_id=1000
        )
        
        # Iniciar servicios
        model_config = get_optimal_models_config()
        logger.info(f"Configuración de modelos: {model_config['total_size_mb']}MB total")
        
        # Actualizar estado
        self.is_active = True
        
        return True
    
    def stop(self) -> bool:
        """
        Detiene el asistente y sus servicios.
        
        Returns:
            bool: True si se detuvo correctamente
        """
        logger.info("Deteniendo asistente Lala")
        
        # Detener escucha en segundo plano si está activa
        if self.background_thread and self.background_thread.is_alive():
            self.should_stop_background = True
            self.background_thread.join(timeout=2.0)
        
        # Detener servicios en Android
        self.android.voice_recognition.stop_listening()
        self.android.text_to_speech.stop_speaking()
        self.android.notifications.cancel_notification(1000)
        
        # Actualizar estado
        self.is_active = False
        
        return True
    
    def process_text_command(self, command_text: str, 
                         wait_for_response: bool = True) -> Dict[str, Any]:
        """
        Procesa un comando de texto.
        
        Args:
            command_text: Texto del comando
            wait_for_response: Si es True, espera a que se complete el procesamiento
            
        Returns:
            Dict: Respuesta del procesamiento
        """
        logger.info(f"Procesando comando de texto: '{command_text}'")
        
        # Actualizar estado
        self.is_processing = True
        self.last_command = command_text
        
        # Verificar conexión online
        online_available = is_online() and not self.prefer_offline
        
        try:
            # Procesar con el agente planner
            planning_result = agent_planner.process_command(command_text, self.user_id)
            
            # Generar respuesta con el modelo adecuado
            model_response = process_ai_request(
                prompt=f"Responde al usuario que dice: '{command_text}'",
                system_prompt="Eres Lala, un asistente de voz amable y servicial. " + 
                            "Responde de forma concisa y natural.",
                model_preference="auto" if online_available else "offline",
                max_tokens=200
            )
            
            # Integrar respuesta del modelo con el plan
            if planning_result.get("success", False):
                response_text = planning_result.get("response", "")
            else:
                response_text = model_response.get("text", "") or model_response.get("response", "")
            
            # Reproducir respuesta por voz
            if wait_for_response:
                self.android.text_to_speech.speak(response_text)
            
            # Crear resultado integrado
            result = {
                "success": True,
                "response": response_text,
                "plan": planning_result.get("plan", {}),
                "model": model_response.get("model", "offline"),
                "online": online_available
            }
            
            # Guardar respuesta
            self.last_response = result
            
        except Exception as e:
            logger.error(f"Error al procesar comando: {e}")
            
            # Respuesta de error
            result = {
                "success": False,
                "response": "Lo siento, ocurrió un error al procesar tu solicitud.",
                "error": str(e)
            }
        
        # Actualizar estado
        self.is_processing = False
        
        return result
    
    def process_voice_command(self, max_duration_sec: int = 5) -> Dict[str, Any]:
        """
        Graba y procesa un comando de voz.
        
        Args:
            max_duration_sec: Duración máxima de grabación en segundos
            
        Returns:
            Dict: Respuesta del procesamiento
        """
        logger.info("Grabando comando de voz...")
        
        # Iniciar grabación con feedback
        self.android.bridge.vibrate(100)  # Vibración corta
        self.android.bridge.show_toast("Escuchando...")
        
        # Obtener texto reconocido
        recognized_text = self.android.voice_recognition.recognize_once(max_duration_sec)
        
        if not recognized_text:
            return {
                "success": False,
                "response": "No se detectó ningún comando de voz.",
                "recognized_text": ""
            }
        
        logger.info(f"Texto reconocido: '{recognized_text}'")
        
        # Extraer comando tras la palabra de activación
        command_text = recognized_text
        
        # Buscar palabra de activación
        wake_word_lower = self.wake_word.lower()
        if wake_word_lower in recognized_text.lower():
            # Extraer texto después de la palabra de activación
            start_index = recognized_text.lower().find(wake_word_lower) + len(wake_word_lower)
            command_text = recognized_text[start_index:].strip()
        
        # Procesar el comando de texto
        result = self.process_text_command(command_text)
        
        # Añadir información de reconocimiento
        result["recognized_text"] = recognized_text
        
        return result
    
    def start_background_listening(self, 
                                callback: Optional[Callable[[Dict[str, Any]], None]] = None) -> bool:
        """
        Inicia escucha continua en segundo plano.
        
        Args:
            callback: Función a llamar cuando se detecte un comando
            
        Returns:
            bool: True si se inició correctamente
        """
        logger.info("Iniciando escucha continua en segundo plano")
        
        # Si ya hay un hilo en ejecución, detenerlo
        if self.background_thread and self.background_thread.is_alive():
            self.should_stop_background = True
            self.background_thread.join(timeout=2.0)
        
        # Reiniciar flag
        self.should_stop_background = False
        
        # Iniciar servicio en Android
        self.android.start_background_assistant()
        
        # Iniciar thread para escucha continua
        def background_listening_thread():
            logger.info("Hilo de escucha continua iniciado")
            
            while not self.should_stop_background:
                try:
                    # En implementación real: escuchar continuamente con Vosk
                    # Para este prototipo, simular detección ocasional
                    
                    # Esperar un tiempo 
                    time.sleep(2.0)
                    
                    # Simular detección de palabra de activación
                    if not self.is_processing and not self.should_stop_background:
                        # Procesar comando
                        result = self.process_voice_command()
                        
                        # Llamar a callback si existe
                        if callback and callable(callback) and result.get("success", False):
                            callback(result)
                
                except Exception as e:
                    logger.error(f"Error en hilo de escucha: {e}")
                    time.sleep(1.0)  # Esperar antes de reintentar
        
        # Iniciar hilo
        self.background_thread = threading.Thread(
            target=background_listening_thread,
            daemon=True
        )
        self.background_thread.start()
        
        return True
    
    def stop_background_listening(self) -> bool:
        """
        Detiene la escucha continua en segundo plano.
        
        Returns:
            bool: True si se detuvo correctamente
        """
        logger.info("Deteniendo escucha continua en segundo plano")
        
        # Detener hilo
        if self.background_thread and self.background_thread.is_alive():
            self.should_stop_background = True
            self.background_thread.join(timeout=2.0)
        
        # Detener servicio en Android
        self.android.stop_background_assistant()
        
        return True
    
    def set_wake_word(self, wake_word: str) -> bool:
        """
        Cambia la palabra de activación.
        
        Args:
            wake_word: Nueva palabra de activación
            
        Returns:
            bool: True si se cambió correctamente
        """
        if not wake_word or len(wake_word) < 2:
            logger.warning(f"Palabra de activación inválida: '{wake_word}'")
            return False
        
        logger.info(f"Cambiando palabra de activación a: '{wake_word}'")
        self.wake_word = wake_word
        
        # Notificar al usuario
        self.android.bridge.show_toast(f"Palabra de activación cambiada a: {wake_word}")
        
        return True
    
    def set_offline_preference(self, prefer_offline: bool) -> bool:
        """
        Cambia la preferencia de modo offline.
        
        Args:
            prefer_offline: Si es True, prioriza procesamiento offline
            
        Returns:
            bool: True si se cambió correctamente
        """
        logger.info(f"Cambiando preferencia offline a: {prefer_offline}")
        
        self.prefer_offline = prefer_offline
        ai_router.offline_mode = prefer_offline
        
        # Notificar al usuario
        mode_name = "offline" if prefer_offline else "online cuando sea posible"
        self.android.bridge.show_toast(f"Modo cambiado a: {mode_name}")
        
        return True
    
    def get_configuration(self) -> Dict[str, Any]:
        """
        Obtiene la configuración actual del asistente.
        
        Returns:
            Dict: Configuración actual
        """
        # Obtener estado del dispositivo
        device_status = self.android.get_device_status()
        
        # Obtener configuración de modelos
        model_config = get_optimal_models_config()
        
        return {
            "wake_word": self.wake_word,
            "prefer_offline": self.prefer_offline,
            "is_active": self.is_active,
            "is_processing": self.is_processing,
            "device": device_status,
            "models": {
                "total_size_mb": model_config["total_size_mb"],
                "max_size_mb": model_config["max_size_mb"],
                "models": model_config["models"]
            },
            "online_available": is_online(),
            "api_services": {
                "openai": ai_router.available_services.count("openai") > 0,
                "anthropic": ai_router.available_services.count("anthropic") > 0,
                "gemini": ai_router.available_services.count("gemini") > 0,
                "grok": ai_router.available_services.count("grok") > 0
            }
        }


# Crear una instancia global del asistente para uso fácil
assistant = LalaAssistant()


# API principales para usar desde Java/Kotlin
def initialize_assistant(user_id: Optional[int] = None, 
                       prefer_offline: bool = False,
                       wake_word: str = "Lala") -> Dict[str, Any]:
    """
    API para inicializar el asistente desde Android.
    
    Args:
        user_id: ID del usuario actual (opcional)
        prefer_offline: Si es True, prioriza procesamiento offline
        wake_word: Palabra de activación para comandos de voz
        
    Returns:
        Dict: Resultado de la inicialización
    """
    global assistant
    
    try:
        # Crear nueva instancia con parámetros específicos
        assistant = LalaAssistant(user_id, prefer_offline, wake_word)
        
        # Iniciar asistente
        success = assistant.start()
        
        return {
            "success": success,
            "config": assistant.get_configuration()
        }
    
    except Exception as e:
        logger.error(f"Error al inicializar asistente: {e}")
        
        return {
            "success": False,
            "error": str(e)
        }


def process_command(command_text: str) -> Dict[str, Any]:
    """
    API para procesar un comando de texto desde Android.
    
    Args:
        command_text: Texto del comando
        
    Returns:
        Dict: Respuesta del procesamiento
    """
    global assistant
    
    try:
        # Verificar si es una demostración y simular respuestas
        if "--demo" in sys.argv or "--offline" in sys.argv:
            # Simulación de respuestas predefinidas para demo
            if "hola" in command_text.lower():
                return {
                    "success": True,
                    "response": "¡Hola! Soy Lala, tu asistente virtual. ¿En qué puedo ayudarte hoy?",
                    "model": "offline-demo",
                    "online": False
                }
            elif "qué puedes hacer" in command_text.lower() or "funciones" in command_text.lower():
                return {
                    "success": True,
                    "response": "Puedo ayudarte con muchas cosas: responder preguntas, configurar alarmas, enviar mensajes, controlar aplicaciones, obtener información del clima y noticias, y mucho más. Estoy diseñada para funcionar incluso sin internet.",
                    "model": "offline-demo",
                    "online": False
                }
            elif "clima" in command_text.lower():
                return {
                    "success": True,
                    "response": "En Madrid el clima está parcialmente nublado con una temperatura de 22°C. La humedad es del 65% y hay viento de 10 km/h.",
                    "model": "offline-demo",
                    "online": False,
                    "plan": {"action": "get_weather", "location": "Madrid"}
                }
            elif "alarma" in command_text.lower():
                return {
                    "success": True,
                    "response": "He configurado una alarma para las 8:00 de la mañana.",
                    "model": "offline-demo",
                    "online": False,
                    "plan": {"action": "set_alarm", "time": "8:00 AM"}
                }
            elif "mensaje" in command_text.lower():
                return {
                    "success": True,
                    "response": "He enviado tu mensaje a Juan por WhatsApp.",
                    "model": "offline-demo",
                    "online": False,
                    "plan": {"action": "send_message", "app": "whatsapp", "contact": "Juan", "message": "Llegaré tarde"}
                }
            elif "abre" in command_text.lower() or "abrir" in command_text.lower():
                app_name = "mapas"
                if "mapas" in command_text.lower():
                    app_name = "mapas"
                elif "youtube" in command_text.lower():
                    app_name = "youtube"
                elif "cámara" in command_text.lower():
                    app_name = "cámara"
                
                return {
                    "success": True,
                    "response": f"Abriendo la aplicación de {app_name}.",
                    "model": "offline-demo",
                    "online": False,
                    "plan": {"action": "open_app", "app_name": app_name}
                }
            else:
                # Respuesta genérica para otros comandos
                return {
                    "success": True,
                    "response": f"Entiendo tu solicitud: '{command_text}'. En un dispositivo real, podría ejecutar esta acción correctamente.",
                    "model": "offline-demo",
                    "online": False
                }
                
        # Procesar comando normalmente
        return assistant.process_text_command(command_text)
    
    except Exception as e:
        logger.error(f"Error al procesar comando: {e}")
        
        # En caso de error en modo demo, proporcionar respuesta simulada
        if "--demo" in sys.argv:
            return {
                "success": True,
                "response": f"Entiendo tu solicitud: '{command_text}'. En un dispositivo real, podría ejecutar esta acción correctamente.",
                "model": "offline-demo",
                "online": False,
                "simulated": True
            }
        
        return {
            "success": False,
            "error": str(e)
        }


def listen_for_command() -> Dict[str, Any]:
    """
    API para grabar y procesar un comando de voz desde Android.
    
    Returns:
        Dict: Respuesta del procesamiento
    """
    global assistant
    
    try:
        return assistant.process_voice_command()
    
    except Exception as e:
        logger.error(f"Error al procesar comando de voz: {e}")
        
        return {
            "success": False,
            "error": str(e)
        }


def start_continuous_listening() -> Dict[str, Any]:
    """
    API para iniciar escucha continua desde Android.
    
    Returns:
        Dict: Resultado de la operación
    """
    global assistant
    
    try:
        success = assistant.start_background_listening()
        
        return {
            "success": success
        }
    
    except Exception as e:
        logger.error(f"Error al iniciar escucha continua: {e}")
        
        return {
            "success": False,
            "error": str(e)
        }


def stop_continuous_listening() -> Dict[str, Any]:
    """
    API para detener escucha continua desde Android.
    
    Returns:
        Dict: Resultado de la operación
    """
    global assistant
    
    try:
        success = assistant.stop_background_listening()
        
        return {
            "success": success
        }
    
    except Exception as e:
        logger.error(f"Error al detener escucha continua: {e}")
        
        return {
            "success": False,
            "error": str(e)
        }


def update_configuration(config: Dict[str, Any]) -> Dict[str, Any]:
    """
    API para actualizar configuración del asistente desde Android.
    
    Args:
        config: Nuevos valores de configuración
        
    Returns:
        Dict: Configuración actualizada
    """
    global assistant
    
    try:
        # Actualizar configuraciones
        if "wake_word" in config:
            assistant.set_wake_word(config["wake_word"])
        
        if "prefer_offline" in config:
            assistant.set_offline_preference(config["prefer_offline"])
        
        return {
            "success": True,
            "config": assistant.get_configuration()
        }
    
    except Exception as e:
        logger.error(f"Error al actualizar configuración: {e}")
        
        return {
            "success": False,
            "error": str(e)
        }


def get_assistant_status() -> Dict[str, Any]:
    """
    API para obtener estado actual del asistente desde Android.
    
    Returns:
        Dict: Estado actual
    """
    global assistant
    
    try:
        return {
            "success": True,
            "status": {
                "is_active": assistant.is_active,
                "is_processing": assistant.is_processing,
                "last_command": assistant.last_command,
                "online_available": is_online(),
                "device": assistant.android.get_device_status()
            }
        }
    
    except Exception as e:
        logger.error(f"Error al obtener estado: {e}")
        
        return {
            "success": False,
            "error": str(e)
        }