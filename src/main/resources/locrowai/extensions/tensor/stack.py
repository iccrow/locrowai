import torch
from pydantic import BaseModel, ConfigDict

from api import Function, register

class StackParams(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    tensor1: torch.Tensor
    tensor2: torch.Tensor

class StackReturns(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    tensor: torch.Tensor

@register("/tensor/stack")
class StackFunc(Function[StackParams, StackReturns]):

    def exec(self):
        stacked = torch.vstack([self.params.tensor1, self.params.tensor2])
        
        self.returns = StackReturns(tensor=stacked)