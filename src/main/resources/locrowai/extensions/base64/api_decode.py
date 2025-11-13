import base64
from pydantic import BaseModel

from api.extensions import Function, register

class DecodeParams(BaseModel):
    base64: str

class DecodeReturns(BaseModel):
    bytes: bytes

@register("/base64/decode")
class DecodeFunc(Function[DecodeParams, DecodeReturns]):

    def exec(self):
        self.returns = DecodeReturns(bytes=base64.b64decode(self.params.base64))

    @staticmethod
    def warmup():
        params = DecodeParams(base64=base64.b64encode(b"sample").decode())
        func = DecodeFunc(params=params)
        func.exec()