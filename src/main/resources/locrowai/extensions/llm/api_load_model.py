from pydantic import BaseModel
from api.extensions import Function, register
from .model_loader import load_model, unload_model


class LoadModelParams(BaseModel):
    repo_id: str = "bartowski/Llama-3.2-1B-Instruct-GGUF"
    filename: str = "Llama-3.2-1B-Instruct-Q8_0.gguf"
    n_ctx: int = 4096
    n_gpu_layers: int = -1
    verbose: bool = False
    overwrite: bool = True


class LoadModelReturns(BaseModel):
    repo_id: str
    filename: str
    n_ctx: int
    n_gpu_layers: int


@register("/llm/load")
class LoadModelFunc(Function[LoadModelParams, LoadModelReturns]):

    def exec(self):
        # load the model (singleton)
        meta = load_model(
            repo_id=self.params.repo_id,
            filename=self.params.filename,
            n_ctx=self.params.n_ctx,
            n_gpu_layers=self.params.n_gpu_layers,
            verbose=self.params.verbose,
            overwrite=self.params.overwrite
        )
        
        self.returns = LoadModelReturns(repo_id=meta.repo_id, filename=meta.filename,
                                       n_ctx=meta.n_ctx, n_gpu_layers=meta.n_gpu_layers)
    
    @staticmethod
    def warmup():
        params = LoadModelParams()
        func = LoadModelFunc(params=params)
        func.exec()

    @staticmethod
    def freeze():
        unload_model()