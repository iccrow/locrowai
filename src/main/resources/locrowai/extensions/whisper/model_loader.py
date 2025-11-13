from typing import Optional
from faster_whisper import WhisperModel

from api.torch import get_default_device, clear_torch_cache
from api.threading import ModelLock

class _ModelMetadata:
    def __init__(self, size: str, device: str, compute_type: str):
        self.size = size
        self.device = device
        self.compute_type = compute_type

_model: Optional[WhisperModel] = None
_metadata: Optional[_ModelMetadata] = None

def _load_new_model(size: str = "small", device: str = get_default_device(), compute_type: str = "float16") -> tuple[_ModelMetadata, WhisperModel]:
    """Internal function to initialize the WhisperModel."""
    metadata = _ModelMetadata(size=size, device=device, compute_type=compute_type)
    model = WhisperModel(size, device=device, compute_type=compute_type)

    return metadata, model
    
def _unload_model():
    """Unload the currently loaded WhisperModel."""
    global _model, _metadata
    if _model is not None:
        _model = None
        _metadata = None
        clear_torch_cache()

@ModelLock("whisper")
def load_model(size: str = "small", device: str = get_default_device(), compute_type: str = "float16", overwrite: bool = True) -> _ModelMetadata:
    """
    Load and return the WhisperModel singleton.
    Safe to call from multiple threads; lazy-loads the model.
    """
    global _model, _metadata
    if _model is None or overwrite:
        _unload_model()
        _metadata, _model = _load_new_model(size=size, device=device, compute_type=compute_type)
    return _metadata

@ModelLock("whisper")
def unload_model():
    """Unload the currently loaded WhisperModel instance."""
    _unload_model()

def get_model() -> Optional[WhisperModel]:
    """Return the loaded WhisperModel instance, or None if not loaded."""
    return _model