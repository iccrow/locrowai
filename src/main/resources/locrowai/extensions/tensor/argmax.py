import torch
from pydantic import BaseModel, ConfigDict

from api import Function, register

class SimParams(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    tensor: torch.Tensor

class SimReturns(BaseModel):
    index: int

@register("/tensor/argmax")
class SimFunc(Function[SimParams, SimReturns]):

    def exec(self):
        index = self.params.tensor.argmax().item()
        
        self.returns = SimReturns(index=index)