from typing import Any, Dict
from pydantic import BaseModel, ConfigDict

from api import Function, register

class VarParams(BaseModel):
    model_config = ConfigDict(extra="allow", arbitrary_types_allowed=True)

class VarReturns(BaseModel):
    model_config = ConfigDict(extra="allow", arbitrary_types_allowed=True)

@register("/utils/vars")
class VarFunc(Function[VarParams, VarReturns]):

    def exec(self):       
        self.returns = VarReturns()

        for key, val in self.params:
            setattr(self.returns, key, val)