from pydantic import BaseModel
from api import Function, register
from typing import Optional

from .classifier_loader import load_model, unload_model


class LoadModelParams(BaseModel):
    source: str = "speechbrain/spkrec-ecapa-voxceleb"
    savedir: Optional[str] = None
    run_opts: Optional[dict] = None
    overwrite: bool = True


class LoadModelReturns(BaseModel):
    source: str
    savedir: Optional[str]


@register("/audio/load")
class LoadModelFunc(Function[LoadModelParams, LoadModelReturns]):

    def exec(self):
        meta = load_model(
            source=self.params.source,
            savedir=self.params.savedir,
            run_opts=self.params.run_opts,
            overwrite=self.params.overwrite,
        )

        self.returns = LoadModelReturns(source=meta.source, savedir=meta.savedir)

    @staticmethod
    def warmup():
        params = LoadModelParams()
        func = LoadModelFunc(params=params)
        func.exec()

    @staticmethod
    def freeze():
        unload_model()