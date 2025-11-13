import base64
from pydantic import BaseModel

from api.extensions import Function, register

class EncodeParams(BaseModel):
    bytes: bytes

class EncodeReturns(BaseModel):
    base64: str

@register("/base64/encode")
class EncodeFunc(Function[EncodeParams, EncodeReturns]):

    def exec(self):
        self.returns = EncodeReturns(base64=base64.b64encode(self.params.bytes))
    
    @staticmethod
    def warmup():
        params = EncodeParams(bytes=b"sample")
        func = EncodeFunc(params=params)
        func.exec()