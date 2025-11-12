import torch
from pydantic import BaseModel, ConfigDict
import uuid
import os

from api import Function, register

class CacheParams(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    tensor: bytes
    auto_cleanup: bool = True

class CacheReturns(BaseModel):
    path: str

@register("/tensor/cache")
class CacheFunc(Function[CacheParams, CacheReturns]):

    def exec(self):
        path = f"extensions/tensor/cache/{uuid.uuid4()}.pt"
        os.makedirs(os.path.dirname(path), exist_ok=True)

        torch.save(self.params.tensor, path)
        
        
        self.returns = CacheReturns(path=path)

    def cleanup(self):
        if self.params.auto_cleanup:
            os.remove(self.returns.path)
    
    @staticmethod
    def warmup():
        params = CacheParams(
            tensor=torch.zeros(10).numpy().tobytes()
        )
        func = CacheFunc(params=params)
        func.exec()
        func.cleanup()