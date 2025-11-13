from __future__ import annotations
from pydantic import BaseModel, Field, TypeAdapter
from typing import Any, Generic, TypeVar, ClassVar, Type

functions: dict[str, Type[Function]] = {}

def register(id_: str):
    def deco(cls):
        print("[REGISTER] Registering function: " + id_)
        cls.id = id_
        functions[id_] = cls
        return cls
    return deco

P = TypeVar("P", bound=BaseModel)
R = TypeVar("R", bound=BaseModel)

class Function(BaseModel, Generic[P, R]):
    id: ClassVar[str] = "__UNSET__"

    params: P
    returns: R | None = None
    passes: dict[str, Any] = Field(default_factory=dict)

    def exec(self):
        raise NotImplementedError
    
    def cleanup(self):
        pass

    @classmethod
    def parse_params(cls, data: dict) -> P:
        anno = cls.model_fields['params'].annotation
        return TypeAdapter(anno).validate_python(data)
    
    
# class AddParams(BaseModel):
#     a: int = 0
#     b: int = 0

# class AddReturns(BaseModel):
#     value: int = 0

# class AddFunction(Function[AddParams, AddReturns]):
#     id: Literal["add"] = "add"

# add = AddFunction(params=AddParams(a=1,b=2))
# nxt = AddFunction()

# add.feed(nxt=nxt, mapping=Mapping(R2P={"value":"a"}), feeds={"b": 2})