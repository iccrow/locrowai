import gc
import torch

def get_default_device() -> str:
    if torch.cuda.is_available():
        return "cuda"
    elif torch.xpu.is_available():
        return "xpu"
    elif torch.backends.mps.is_available():
        return "mps"
    else:
        return "cpu"

def clear_torch_cache():

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