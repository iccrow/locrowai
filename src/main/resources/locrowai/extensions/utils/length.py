from typing import Any
from pydantic import BaseModel, ConfigDict, field_validator

from api import Function, register

class LengthParams(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    value: Any

    @field_validator("value")
    def must_have_len(cls, v):
        if hasattr(v, '__len__'):
            return v
        else:
            raise ValueError("value does not have a length")

class LengthReturns(BaseModel):
    length: int

@register("/utils/length")
class LengthFunc(Function[LengthParams, LengthReturns]):

    def exec(self):
        length = len(self.params.value)
        
        self.returns = LengthReturns(length=length)

    @staticmethod
    def warmup():
        params = LengthParams(value=[1, 2, 3, 4, 5])
        func = LengthFunc(params=params)
        func.exec()