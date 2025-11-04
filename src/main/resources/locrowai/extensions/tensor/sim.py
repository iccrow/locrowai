import torch
from pydantic import BaseModel, ConfigDict

from api import Function, register

similarity = torch.nn.CosineSimilarity(dim=-1, eps=1e-6)

class SimParams(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    tensor1: torch.Tensor
    tensor2: torch.Tensor

class SimReturns(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    similarities: torch.Tensor

@register("/tensor/sim")
class SimFunc(Function[SimParams, SimReturns]):

    def exec(self):
        similarities = similarity(self.params.tensor1, self.params.tensor2)
        
        self.returns = SimReturns(similarities=similarities)