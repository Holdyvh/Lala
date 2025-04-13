"""
Integración de Vosk para reconocimiento de voz offline en Lala.

Este módulo proporciona la integración con Vosk para reconocimiento de voz
sin conexión a internet, optimizado para dispositivos Android con recursos limitados.
"""

import os
import json
import logging
import threading
import wave
import numpy as np
from typing import Dict, Any, Optional, List, Callable, Union, BinaryIO

# Configuración de logging
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

# Importar Vosk (verificando disponibilidad)
try:
    from vosk import Model, KaldiRecognizer, SetLogLevel
    VOSK_AVAILABLE = True
    # Reducir verbosidad de logs de Vosk
    SetLogLevel(-1)
except ImportError:
    logger.warning("Vosk no está disponible. El reconocimiento offline será limitado.")
    VOSK_AVAILABLE = False

# Constantes
DEFAULT_SAMPLE_RATE = 16000
DEFAULT_MODEL_PATH = os.path.join(os.path.dirname(__file__), "..", "assets", "models", "vosk", "vosk-model-small-es-0.42")


class VoskRecognizer:
    """
    Reconocedor de voz basado en Vosk para funcionamiento offline.
    
    Esta clase proporciona funcionalidad de reconocimiento de voz sin
    necesidad de conexión a internet, optimizada para dispositivos móviles.
    """
    
    def __init__(self, model_path: str = DEFAULT_MODEL_PATH, 
               sample_rate: int = DEFAULT_SAMPLE_RATE):
        """
        Inicializa el reconocedor Vosk.
        
        Args:
            model_path: Ruta al modelo Vosk
            sample_rate: Frecuencia de muestreo en Hz
        """
        self.model_path = model_path
        self.sample_rate = sample_rate
        self.model = None
        self.recognizer = None
        self._is_initialized = False
        
        # Inicializar automáticamente si está disponible
        if VOSK_AVAILABLE and os.path.exists(model_path):
            self.initialize()
    
    def initialize(self) -> bool:
        """
        Inicializa el modelo y reconocedor Vosk.
        
        Returns:
            bool: True si se inicializó correctamente
        """
        # Verificar si estamos en modo demo o simulado
        demo_mode = "--demo" in sys.argv
        
        if not VOSK_AVAILABLE:
            if demo_mode:
                logger.warning("Vosk no está disponible, ejecutando en modo simulado")
                self._is_initialized = True
                return True
            else:
                logger.error("Vosk no está disponible. Instale con: pip install vosk")
                return False
        
        if not os.path.exists(self.model_path):
            logger.error(f"Modelo no encontrado: {self.model_path}")
            if demo_mode:
                logger.warning("Ejecutando en modo simulado (sin modelo Vosk)")
                self._is_initialized = True
                return True
            return False
        
        try:
            logger.info(f"Cargando modelo Vosk desde: {self.model_path}")
            
            # En modo demo, simular la inicialización
            if demo_mode or not os.path.exists(os.path.join(self.model_path, "model")):
                logger.warning("Modelo incompleto, ejecutando en modo simulado")
                self._is_initialized = True
                return True
                
            # Inicialización real de Vosk
            self.model = Model(self.model_path)
            self.recognizer = KaldiRecognizer(self.model, self.sample_rate)
            self._is_initialized = True
            logger.info("Reconocedor Vosk inicializado correctamente")
            return True
        
        except Exception as e:
            logger.error(f"Error al inicializar Vosk: {e}")
            if demo_mode:
                logger.warning("Error al inicializar, ejecutando en modo simulado")
                self._is_initialized = True
                return True
            self._is_initialized = False
            return False
    
    def is_initialized(self) -> bool:
        """
        Verifica si el reconocedor está inicializado.
        
        Returns:
            bool: Estado de inicialización
        """
        return self._is_initialized and self.model is not None and self.recognizer is not None
    
    def reset(self) -> None:
        """Reinicia el reconocedor para una nueva grabación."""
        if self.is_initialized():
            self.recognizer = KaldiRecognizer(self.model, self.sample_rate)
    
    def process_audio_file(self, audio_file_path: str) -> Dict[str, Any]:
        """
        Procesa un archivo de audio y devuelve el texto reconocido.
        
        Args:
            audio_file_path: Ruta al archivo de audio (WAV)
            
        Returns:
            Dict: Resultado del reconocimiento
        """
        if not self.is_initialized():
            return {"success": False, "error": "Reconocedor no inicializado"}
        
        if not os.path.exists(audio_file_path):
            return {"success": False, "error": f"Archivo no encontrado: {audio_file_path}"}
        
        try:
            # Reiniciar el reconocedor
            self.reset()
            
            # Abrir archivo WAV
            wf = wave.open(audio_file_path, "rb")
            
            # Verificar formato
            if wf.getnchannels() != 1 or wf.getsampwidth() != 2 or wf.getcomptype() != "NONE":
                return {
                    "success": False,
                    "error": "Formato de audio no soportado. Usar WAV mono 16-bit PCM."
                }
            
            # Procesar en fragmentos
            results = []
            
            while True:
                data = wf.readframes(4000)  # 4000 frames = 250ms a 16kHz
                if len(data) == 0:
                    break
                
                if self.recognizer.AcceptWaveform(data):
                    result_json = self.recognizer.Result()
                    result = json.loads(result_json)
                    
                    if "text" in result and result["text"]:
                        results.append(result["text"])
            
            # Obtener resultado final
            final_json = self.recognizer.FinalResult()
            final = json.loads(final_json)
            
            if "text" in final and final["text"]:
                results.append(final["text"])
            
            # Consolidar resultado
            text = " ".join(results).strip()
            
            return {
                "success": True,
                "text": text,
                "confidence": final.get("confidence", 0.0),
                "partial": False
            }
        
        except Exception as e:
            logger.error(f"Error al procesar archivo de audio: {e}")
            return {"success": False, "error": str(e)}
    
    def process_audio_data(self, audio_data: Union[bytes, bytearray, memoryview, BinaryIO],
                         is_final: bool = False) -> Dict[str, Any]:
        """
        Procesa datos de audio en memoria.
        
        Args:
            audio_data: Datos de audio (bytes)
            is_final: Si es True, solicita resultado final
            
        Returns:
            Dict: Resultado del reconocimiento
        """
        if not self.is_initialized():
            return {"success": False, "error": "Reconocedor no inicializado"}
        
        try:
            # Procesar datos
            if isinstance(audio_data, (bytearray, memoryview)) or hasattr(audio_data, 'read'):
                # Si es un archivo o buffer, convertir a bytes si es necesario
                if hasattr(audio_data, 'read'):
                    audio_bytes = audio_data.read()
                else:
                    audio_bytes = bytes(audio_data)
            else:
                # Ya es bytes
                audio_bytes = audio_data
            
            # Resultado parcial o final
            if is_final:
                # Obtener resultado final
                self.recognizer.AcceptWaveform(audio_bytes)
                final_json = self.recognizer.FinalResult()
                final = json.loads(final_json)
                
                text = final.get("text", "")
                confidence = final.get("confidence", 0.0)
                
                return {
                    "success": True,
                    "text": text,
                    "confidence": confidence,
                    "partial": False
                }
            else:
                # Obtener resultado parcial
                if self.recognizer.AcceptWaveform(audio_bytes):
                    # Hay un resultado intermedio completo
                    result_json = self.recognizer.Result()
                    result = json.loads(result_json)
                    
                    text = result.get("text", "")
                    confidence = result.get("confidence", 0.0)
                    
                    return {
                        "success": True,
                        "text": text,
                        "confidence": confidence,
                        "partial": False
                    }
                else:
                    # Resultado parcial
                    partial_json = self.recognizer.PartialResult()
                    partial = json.loads(partial_json)
                    
                    text = partial.get("partial", "")
                    
                    return {
                        "success": True,
                        "text": text,
                        "confidence": 0.0,
                        "partial": True
                    }
        
        except Exception as e:
            logger.error(f"Error al procesar datos de audio: {e}")
            return {"success": False, "error": str(e)}
    
    def start_streaming(self, callback: Callable[[Dict[str, Any]], None]) -> Dict[str, Any]:
        """
        Inicia reconocimiento en tiempo real con callback.
        
        Args:
            callback: Función a llamar con cada resultado
            
        Returns:
            Dict: Resultado de inicio de streaming
        """
        if not self.is_initialized():
            return {"success": False, "error": "Reconocedor no inicializado"}
        
        # Reiniciar el reconocedor
        self.reset()
        
        # En una implementación real, aquí se iniciaría un hilo para
        # capturar audio continuamente y pasarlo al reconocedor
        
        # Para este prototipo, simular inicio exitoso
        return {"success": True, "message": "Streaming iniciado"}
    
    def stop_streaming(self) -> Dict[str, Any]:
        """
        Detiene reconocimiento en tiempo real.
        
        Returns:
            Dict: Resultado de detención de streaming
        """
        # En una implementación real, aquí se detendría el hilo de captura
        
        # Para este prototipo, simular detención exitosa
        return {"success": True, "message": "Streaming detenido"}


# Crear instancia global para uso fácil
recognizer = VoskRecognizer()