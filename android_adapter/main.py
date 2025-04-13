"""
Punto de entrada principal para la versión Android de Lala.

Este módulo proporciona la función main() que inicia la aplicación Lala
en un entorno Android, configurando componentes y servicios necesarios.
"""

import os
import sys
import logging
import json
import argparse
from typing import Dict, Any, Optional

# Configurar logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("lala_android")

# Importar componentes de Lala
from .core import get_android_adapter
from .integration import initialize_assistant, assistant
from .vosk_integration import recognizer as vosk_recognizer
from .tts_integration import tts_engine
from services.model_optimizer import get_optimal_models_config
from services.minilm_nlp import nlp_processor, process_text
from services.agent_planner import agent_planner
from services.ai_models_router import ai_router


def ensure_directories() -> None:
    """Crea directorios necesarios para la aplicación."""
    directories = [
        os.path.join(os.path.dirname(__file__), "..", "assets"),
        os.path.join(os.path.dirname(__file__), "..", "assets", "models"),
        os.path.join(os.path.dirname(__file__), "..", "assets", "audio"),
        os.path.join(os.path.dirname(__file__), "..", "assets", "data"),
        os.path.join(os.path.dirname(__file__), "..", "assets", "temp")
    ]
    
    for directory in directories:
        if not os.path.exists(directory):
            os.makedirs(directory, exist_ok=True)
            logger.info(f"Creado directorio: {directory}")


def check_requirements() -> Dict[str, bool]:
    """
    Verifica requisitos del sistema.
    
    Returns:
        Dict[str, bool]: Estado de los requisitos
    """
    requirements = {
        "vosk_available": hasattr(vosk_recognizer, "is_initialized") and vosk_recognizer.is_initialized(),
        "tts_available": hasattr(tts_engine, "is_initialized") and tts_engine.is_initialized(),
        "nlp_available": hasattr(nlp_processor, "is_initialized") and nlp_processor.is_initialized(),
        "agent_planner_available": agent_planner is not None,
        "ai_router_available": ai_router is not None,
        "openai_available": "openai" in ai_router.available_services if hasattr(ai_router, "available_services") else False,
        "anthropic_available": "anthropic" in ai_router.available_services if hasattr(ai_router, "available_services") else False,
        "gemini_available": "gemini" in ai_router.available_services if hasattr(ai_router, "available_services") else False,
        "grok_available": "grok" in ai_router.available_services if hasattr(ai_router, "available_services") else False
    }
    
    # Resumen de requisitos
    available_count = sum(1 for v in requirements.values() if v)
    total_count = len(requirements)
    
    logger.info(f"Requisitos disponibles: {available_count}/{total_count}")
    
    return requirements


def initialize_components(model_size_mb: int = 350, prefer_offline: bool = False) -> Dict[str, Any]:
    """
    Inicializa componentes principales de Lala.
    
    Args:
        model_size_mb: Tamaño máximo de modelos en MB
        prefer_offline: Si es True, prioriza procesamiento offline
        
    Returns:
        Dict[str, Any]: Resultados de la inicialización
    """
    results = {}
    
    try:
        # Asegurar directorios
        ensure_directories()
        
        # Obtener configuración óptima de modelos
        model_config = get_optimal_models_config(model_size_mb)
        results["model_config"] = model_config
        
        # Inicializar procesador NLP
        if not nlp_processor.is_initialized():
            nlp_initialized = nlp_processor.initialize()
            results["nlp_initialized"] = nlp_initialized
        else:
            results["nlp_initialized"] = True
        
        # Inicializar reconocedor de voz Vosk
        if not vosk_recognizer.is_initialized():
            vosk_initialized = vosk_recognizer.initialize()
            results["vosk_initialized"] = vosk_initialized
        else:
            results["vosk_initialized"] = True
        
        # Inicializar motor TTS
        if not tts_engine.is_initialized():
            tts_initialized = tts_engine.initialize()
            results["tts_initialized"] = tts_initialized
        else:
            results["tts_initialized"] = True
        
        # Configurar preferencia offline
        ai_router.offline_mode = prefer_offline
        results["offline_mode"] = prefer_offline
        
        return results
    
    except Exception as e:
        logger.error(f"Error al inicializar componentes: {e}")
        results["error"] = str(e)
        return results


def main(args: Optional[argparse.Namespace] = None) -> int:
    """
    Función principal para iniciar la aplicación.
    
    Args:
        args: Argumentos de línea de comandos
        
    Returns:
        int: Código de salida
    """
    if args is None:
        # Configurar parser de argumentos
        parser = argparse.ArgumentParser(description="Lala Assistant para Android")
        parser.add_argument("--offline", action="store_true", help="Ejecutar en modo offline")
        parser.add_argument("--model-size", type=int, default=350, help="Tamaño máximo de modelos en MB")
        parser.add_argument("--debug", action="store_true", help="Habilitar logs de depuración")
        parser.add_argument("--user-id", type=int, help="ID de usuario para personalización")
        parser.add_argument("--wake-word", type=str, default="Lala", help="Palabra de activación")
        
        args = parser.parse_args()
    
    # Configurar nivel de logging
    if args.debug:
        logger.setLevel(logging.DEBUG)
        logging.getLogger().setLevel(logging.DEBUG)
    
    try:
        logger.info("Iniciando Lala Assistant para Android")
        
        # Inicializar componentes
        init_results = initialize_components(
            model_size_mb=args.model_size,
            prefer_offline=args.offline
        )
        
        if "error" in init_results:
            logger.error(f"Error al inicializar: {init_results['error']}")
            return 1
        
        # Verificar requisitos
        requirements = check_requirements()
        
        # Inicializar asistente
        assistant_init = initialize_assistant(
            user_id=args.user_id,
            prefer_offline=args.offline,
            wake_word=args.wake_word
        )
        
        if not assistant_init.get("success", False):
            logger.error(f"Error al inicializar asistente: {assistant_init.get('error', 'Error desconocido')}")
            return 1
        
        logger.info(f"Asistente inicializado correctamente con wake word: '{args.wake_word}'")
        
        # En una aplicación real, aquí se integraría con el ciclo de vida de Android
        # Para este prototipo, simplemente devolvemos éxito
        
        return 0
        
    except Exception as e:
        logger.error(f"Error en función main: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())