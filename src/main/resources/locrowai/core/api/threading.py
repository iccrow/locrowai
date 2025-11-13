from threading import Lock
from typing import Optional
import functools

_lock = Lock()
_model_locks: dict[str, Lock] = {}

def _get_model_lock(model_id: str) -> Lock:
    with _lock:
        if model_id not in _model_locks:
            _model_locks[model_id] = Lock()
        return _model_locks[model_id]
    
class ModelLock:
    def __init__(self, model_id: str, timeout: Optional[float] = -1):
        self.model_id = model_id
        self.timeout = timeout
        self.lock = _get_model_lock(model_id)

    def __enter__(self):
        acquired = self.lock.acquire(timeout=self.timeout)
        if not acquired:
            raise TimeoutError(f"Could not acquire lock for model {self.model_id} within {self.timeout} seconds")

    def __exit__(self, exc_type, exc_value, traceback):
        self.lock.release()
    
    def __call__(self, func):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            acquired = self.lock.acquire(timeout=self.timeout)
            if not acquired:
                raise TimeoutError(f"Could not acquire lock for model {self.model_id} within {self.timeout} seconds")
            try:
                return func(*args, **kwargs)
            finally:
                self.lock.release()
        return wrapper
    
# Example usage:

# @ModelLock("global_lock")
# def global_synchronized_function():
#     pass

# with ModelLock("my_model", timeout=5.0):
#     pass