from pydantic import BaseModel
from api.extensions import Function, register
from api.torch import get_default_device, get_best_dtype_for_device
from typing import Optional

from .model_loader import load_model, get_model, unload_model


class LoadModelParams(BaseModel):
    size: str = "small"
    device: str = get_default_device()
    dtype: Optional[str] = None
    overwrite: bool = True


class LoadModelReturns(BaseModel):
    size: str
    device: str
    dtype: str


@register("/whisper/load")
class LoadModelFunc(Function[LoadModelParams, LoadModelReturns]):

    def exec(self):
        # Determine device
        device = self.params.device

        # Pick the best dtype for this device
        dtype = self.params.dtype
        if dtype is None:
            dtype = get_best_dtype_for_device(device, True)

        # Load the singleton model
        meta = load_model(size=self.params.size, device=device, compute_type=dtype, overwrite=self.params.overwrite)

        self.returns = LoadModelReturns(
            size=meta.size,
            device=meta.device,
            dtype=meta.compute_type
        )

    @staticmethod
    def warmup():
        params = LoadModelParams()
        func = LoadModelFunc(params=params)
        func.exec()

    @staticmethod
    def freeze():
        unload_model()
