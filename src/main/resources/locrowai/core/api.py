from __future__ import annotations
from pydantic import BaseModel, Field, ValidationError, TypeAdapter
from typing import Any, Generic, TypeVar, ClassVar, Type
import gc

functions: dict[str, Type[Function]] = {}

def register(id_: str):
    def deco(cls):
        print("[REGISTER] Registering function: " + id_)
        cls.id = id_
        functions[id_] = cls
        return cls
    return deco

def get_default_device() -> str:
    import torch
    if torch.cuda.is_available():
        return "cuda"
    elif torch.xpu.is_available():
        return "xpu"
    elif torch.backends.mps.is_available():
        return "mps"
    else:
        return "cpu"

def clear_torch_cache():
    import torch

    if torch.cuda.is_available():
        torch.cuda.empty_cache()
    if torch.xpu.is_available():
        torch.xpu.empty_cache()
    if torch.backends.mps.is_available():
        torch.mps.empty_cache()
    gc.collect()

def get_best_dtype_for_device(device: str, quantize: bool = False) -> str:
    """
    Returns the best dtype to use on the given device for inference:
    'float16', 'float32', or 'int8'.
    
    Supports CUDA, XPU (Intel), and Metal (MPS).
    """
    import torch

    fallback = "int8" if quantize else "float32"

    if device.startswith("cuda"):
        if not torch.cuda.is_available():
            return fallback
        try:
            idx = int(device.split(":")[1]) if ":" in device else 0
            prop = torch.cuda.get_device_properties(idx)
            major = prop.major
            minor = prop.minor
            compute_capability = major + minor / 10.0
            if compute_capability >= 7.0:  # tensor cores
                return "float16"
            elif compute_capability >= 5.3:  # FP16 supported but slower
                return "float16"
            else:
                return fallback
        except Exception:
            return fallback
    elif device.startswith("xpu"):
        return "float16"
    elif device.startswith("mps"):
        return "float16"
    else:
        return fallback

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
    
    # @classmethod
    # def parse_returns(cls, data: dict) -> R:
    #     return cls.returns_model.model_validate(data)
    
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