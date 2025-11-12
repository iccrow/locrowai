import threading
from typing import Optional
import torch
from faster_whisper import WhisperModel

from api import get_default_device, clear_torch_cache

class _ModelMetadata:
    def __init__(self, size: str, device: str, compute_type: str):
        self.size = size
        self.device = device
        self.compute_type = compute_type

_lock = threading.Lock()
_model: Optional[WhisperModel] = None
_metadata: Optional[_ModelMetadata] = None

def _load_new_model(size: str = "small", device: str = get_default_device(), compute_type: str = "float16") -> tuple[_ModelMetadata, WhisperModel]:
    """Internal function to initialize the WhisperModel."""
    with _lock:
        metadata = _ModelMetadata(size=size, device=device, compute_type=compute_type)
        model = WhisperModel(size, device=device, compute_type=compute_type)

        return metadata, model


def load_model(size: str = "small", device: str = get_default_device(), compute_type: str = "float16", overwrite: bool = True) -> _ModelMetadata:
    """
    Load and return the WhisperModel singleton.
    Safe to call from multiple threads; lazy-loads the model.
    """
    global _model, _metadata
    if _model is None or overwrite:
        _metadata, _model = _load_new_model(size=size, device=device, compute_type=compute_type)
    return _metadata


def get_model() -> Optional[WhisperModel]:
    """Return the loaded WhisperModel instance, or None if not loaded."""
    return _model


def unload_model():
    """Unload the currently loaded WhisperModel instance."""
    global _model, _metadata
    if _model is not None:
        with _lock:
            _model = None
            _metadata = None
            clear_torch_cache()
