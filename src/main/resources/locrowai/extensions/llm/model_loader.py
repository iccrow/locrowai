from llama_cpp import Llama
from typing import Optional

from api.threading import ModelLock


class _ModelMetadata:
    def __init__(
        self,
        repo_id: str,
        filename: str,
        n_ctx: int,
        n_gpu_layers: int,
        verbose: bool,
    ):
        self.repo_id = repo_id
        self.filename = filename
        self.n_ctx = n_ctx
        self.n_gpu_layers = n_gpu_layers
        self.verbose = verbose

_llm: Optional[Llama] = None
_metadata: Optional[_ModelMetadata] = None

def _load_new_model(
    repo_id: str,
    filename: str,
    n_ctx: int,
    n_gpu_layers: int,
    verbose: bool,
) -> tuple[_ModelMetadata, Llama]:
    return _ModelMetadata(
        repo_id=repo_id,
        filename=filename,
        n_ctx=n_ctx,
        n_gpu_layers=n_gpu_layers,
        verbose=verbose,
    ), Llama.from_pretrained(
        repo_id=repo_id,
        filename=filename,
        n_ctx=n_ctx,
        n_gpu_layers=n_gpu_layers,
        verbose=verbose,
    )

def _unload_model():
    """Unload the currently loaded model."""
    global _llm, _metadata
    if _llm is not None:
        _llm.close()
        _llm = None
        _metadata = None

@ModelLock("llm")
def load_model(
    repo_id: str = "bartowski/Llama-3.2-1B-Instruct-GGUF",
    filename: str = "Llama-3.2-1B-Instruct-Q8_0.gguf",
    n_ctx: int = 4096,
    n_gpu_layers: int = -1,
    verbose: bool = False,
    overwrite: bool = True,
) -> _ModelMetadata:
    """Load and return a singleton Llama instance.

    This is safe to import from other modules. The model is created lazily
    and protected by a lock to avoid double initialization.
    """
    global _llm, _metadata
    if _llm is None or overwrite:
        _unload_model()
        _metadata, _llm = _load_new_model(
            repo_id=repo_id,
            filename=filename,
            n_ctx=n_ctx,
            n_gpu_layers=n_gpu_layers,
            verbose=verbose
        )
    return _metadata

@ModelLock("llm")
def unload_model():
    """Unload the currently loaded model."""
    _unload_model()

def get_llm() -> Llama | None:
    """Return the loaded Llama instance, returns None if no model is loaded."""
    return _llm
