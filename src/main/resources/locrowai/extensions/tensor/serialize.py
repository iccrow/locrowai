import torch
from pydantic import BaseModel, ConfigDict

from api import Function, register

class SerializeParams(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    tensor: torch.Tensor

class SerializeReturns(BaseModel):
    array: list

@register("/tensor/serialize")
class SerializeFunc(Function[SerializeParams, SerializeReturns]):

    def exec(self):
        array = self.params.tensor.tolist()
        
        self.returns = SerializeReturns(array=array)

    @staticmethod
    def warmup():
        params = SerializeParams(
            tensor=torch.randn(10)
        )
        func = SerializeFunc(params=params)
        func.exec()