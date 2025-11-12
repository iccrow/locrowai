from pydantic import BaseModel
from api import Function, register

from .model_loader import load_model, unload_model


class LoadModelParams(BaseModel):
    lang_code: str = "a"
    repo_id: str = "hexgrad/Kokoro-82M"
    overwrite: bool = True


class LoadModelReturns(BaseModel):
    lang_code: str
    repo_id: str


@register("/tts/load")
class LoadModelFunc(Function[LoadModelParams, LoadModelReturns]):

    def exec(self):
        meta = load_model(
            lang_code=self.params.lang_code,
            repo_id=self.params.repo_id,
            overwrite=self.params.overwrite,
        )

        self.returns = LoadModelReturns(
            lang_code=meta.lang_code,
            repo_id=meta.repo_id,
        )

    @staticmethod
    def warmup():
        params = LoadModelParams()
        func = LoadModelFunc(params=params)
        func.exec()

    @staticmethod
    def freeze():
        unload_model()
