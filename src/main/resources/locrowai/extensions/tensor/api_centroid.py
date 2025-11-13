import torch
from pydantic import BaseModel, ConfigDict

from api.extensions import Function, register

class CentroidParams(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    tensor: torch.Tensor
    normalize: bool = False

class CentroidReturns(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    centroid: torch.Tensor

@register("/tensor/centroid")
class CentroidFunc(Function[CentroidParams, CentroidReturns]):

    def exec(self):
        centroid = torch.mean(self.params.tensor, dim=0)

        if self.params.normalize:
            centroid = torch.nn.functional.normalize(centroid, p=2, dim=0)
        
        self.returns = CentroidReturns(centroid=centroid)

    @staticmethod
    def warmup():
        params = CentroidParams(
            tensor=torch.randn(5, 10),
            normalize=True
        )
        func = CentroidFunc(params=params)
        func.exec()