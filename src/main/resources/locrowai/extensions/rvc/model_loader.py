from typing import Optional
from rvc_python.infer import RVCInference

from api.torch import get_default_device, clear_torch_cache
from api.threading import ModelLock

class _ModelMetadata:
    def __init__(
        self,
        model_name: str,
        device: str,
        f0method: str,
        filter_radius: int,
        rms_mix_rate: float,
        f0up_key: int,
    ):
        self.model_name = model_name
        self.device = device
        self.f0method = f0method
        self.filter_radius = filter_radius
        self.rms_mix_rate = rms_mix_rate
        self.f0up_key = f0up_key

_rvc: Optional[RVCInference] = None
_metadata: Optional[_ModelMetadata] = None


def _load_new_model(
    model_name: str,
    device: str,
    f0method: str,
    filter_radius: int,
    rms_mix_rate: float,
    f0up_key: int,
) -> tuple[_ModelMetadata, RVCInference]:
    metadata = _ModelMetadata(
        model_name=model_name,
        device=device,
        f0method=f0method,
        filter_radius=filter_radius,
        rms_mix_rate=rms_mix_rate,
        f0up_key=f0up_key,
    )

    # Initialize inference engine
    model = RVCInference(device=device)

    # Set runtime parameters
    model.set_params(
        f0method=f0method,
        filter_radius=filter_radius,
        rms_mix_rate=rms_mix_rate,
        f0up_key=f0up_key,
    )

    # Load voice model and index
    model_path = f"extensions/rvc/models/{model_name}.pth"
    index_path = f"extensions/rvc/models/{model_name}.index"
    model.load_model(model_path, index_path=index_path)

    return metadata, model

def _unload_model():
    """Unload the currently loaded RVCInference instance."""
    global _rvc, _metadata
    if _rvc is not None:
        _rvc = None
        _metadata = None
        clear_torch_cache()

@ModelLock("rvc")
def load_model(
    model_name: str,
    device: str = get_default_device(),
    f0method: str = "pm",
    filter_radius: int = 2,
    rms_mix_rate: float = 0.5,
    f0up_key: int = -3,
    overwrite: bool = True,
) -> _ModelMetadata:
    """Load and return a singleton RVCInference instance.

    This is safe to import from other modules. The model is created lazily
    and protected by a lock to avoid double initialization.
    """
    global _rvc, _metadata
    if _rvc is None or overwrite:
        _unload_model()
        _metadata, _rvc = _load_new_model(
            model_name=model_name,
            device=device,
            f0method=f0method,
            filter_radius=filter_radius,
            rms_mix_rate=rms_mix_rate,
            f0up_key=f0up_key,
        )
    return _metadata

@ModelLock("rvc")
def unload_model():
    """Unload the currently loaded RVCInference instance."""
    _unload_model()


def get_rvc() -> RVCInference | None:
    """Return the loaded RVCInference instance, or None if not loaded."""
    return _rvc
