from typing import Optional
from kokoro import KPipeline

from api.torch import clear_torch_cache
from api.threading import ModelLock


class _ModelMetadata:
    def __init__(self, lang_code: str, repo_id: str):
        self.lang_code = lang_code
        self.repo_id = repo_id

_pipeline: Optional[KPipeline] = None
_metadata: Optional[_ModelMetadata] = None


def _load_new_pipeline(lang_code: str = "a", repo_id: str = "hexgrad/Kokoro-82M") -> tuple[_ModelMetadata, KPipeline]:
    """Internal function to initialize the Kokoro pipeline."""
    metadata = _ModelMetadata(lang_code=lang_code, repo_id=repo_id)
    pipeline = KPipeline(lang_code=lang_code, repo_id=repo_id)
    return metadata, pipeline

def _unload_pipeline():
    """Unload the currently loaded Kokoro pipeline."""
    global _pipeline, _metadata
    if _pipeline is not None:
        _pipeline = None
        _metadata = None
        clear_torch_cache()

@ModelLock("tts")
def load_model(lang_code: str = "a", repo_id: str = "hexgrad/Kokoro-82M", overwrite: bool = True) -> _ModelMetadata:
    """
    Load and return the Kokoro TTS pipeline singleton.
    Safe to call from multiple threads; lazy-loads the pipeline.
    """
    global _pipeline, _metadata
    if _pipeline is None or overwrite:
        _unload_pipeline()
        _metadata, _pipeline = _load_new_pipeline(lang_code=lang_code, repo_id=repo_id)
    return _metadata

@ModelLock("tts")
def unload_model():
    """Unload the currently loaded KPipeline instance."""
    _unload_pipeline()

def get_pipeline() -> Optional[KPipeline]:
    """Return the loaded KPipeline instance, or None if not loaded."""
    return _pipeline