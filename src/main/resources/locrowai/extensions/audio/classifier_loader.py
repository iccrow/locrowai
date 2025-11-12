import gc
import threading
from typing import Optional
from speechbrain.inference.speaker import EncoderClassifier

from api import clear_torch_cache


class _ModelMetadata:
    def __init__(
        self,
        source: str,
        savedir: Optional[str],
        run_opts: Optional[dict]
    ):
        self.source = source
        self.savedir = savedir
        self.run_opts = run_opts


_lock = threading.Lock()
_classifier: Optional[EncoderClassifier] = None
_metadata: Optional[_ModelMetadata] = None


def _load_new_model(
    source: str,
    savedir: Optional[str],
    run_opts: Optional[dict]
) -> tuple[_ModelMetadata, EncoderClassifier]:
    with _lock:
        metadata = _ModelMetadata(
            source=source,
            savedir=savedir,
            run_opts=run_opts,
        )
        model = EncoderClassifier.from_hparams(
            source=source,
            savedir=savedir,
            run_opts=run_opts,
            freeze_params=True,
        )
        return metadata, model


def load_model(
    source: str = "speechbrain/spkrec-ecapa-voxceleb",
    savedir: Optional[str] = None,
    run_opts: Optional[dict] = None,
    overwrite: bool = True,
) -> _ModelMetadata:
    """Load and return a singleton EncoderClassifier instance.

    This is safe to import from other modules. The model is created lazily
    and protected by a lock to avoid double initialization.
    """
    global _classifier, _metadata
    if _classifier is None or overwrite:
        unload_model()
        _metadata, _classifier = _load_new_model(
            source=source,
            savedir=savedir,
            run_opts=run_opts
        )
    return _metadata


def unload_model():
    """Unload the currently loaded speaker encoder."""
    global _classifier, _metadata
    if _classifier is not None:
        with _lock:
            _classifier
            _classifier = None
            _metadata = None
            clear_torch_cache()


def get_classifier() -> EncoderClassifier | None:
    """Return the loaded EncoderClassifier instance, or None if none is loaded."""
    return _classifier
