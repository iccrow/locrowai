from pydantic import BaseModel
from api import Function, register, get_default_device

from .model_loader import load_model, unload_model


class LoadModelParams(BaseModel):
    model_name: str
    device: str = get_default_device()
    f0method: str = "pm"
    filter_radius: int = 2
    rms_mix_rate: float = 0.5
    f0up_key: int = -3
    overwrite: bool = True


class LoadModelReturns(BaseModel):
    model_name: str
    device: str
    f0method: str
    filter_radius: int
    rms_mix_rate: float
    f0up_key: int


@register("/rvc/load")
class LoadModelFunc(Function[LoadModelParams, LoadModelReturns]):

    def exec(self):
        meta = load_model(
            model_name=self.params.model_name,
            device=self.params.device,
            f0method=self.params.f0method,
            filter_radius=self.params.filter_radius,
            rms_mix_rate=self.params.rms_mix_rate,
            f0up_key=self.params.f0up_key,
            overwrite=self.params.overwrite,
        )

        self.returns = LoadModelReturns(
            model_name=meta.model_name,
            device=meta.device,
            f0method=meta.f0method,
            filter_radius=meta.filter_radius,
            rms_mix_rate=meta.rms_mix_rate,
            f0up_key=meta.f0up_key,
        )

    @staticmethod
    def warmup():
        params = LoadModelParams(model_name="villager/model")
        func = LoadModelFunc(params=params)
        func.exec()

    @staticmethod
    def freeze():
        unload_model()
