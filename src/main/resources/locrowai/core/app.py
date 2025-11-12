from __future__ import annotations
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, TypeAdapter, field_validator
from typing import Any, List, Dict, Literal
import uuid
import re
import json

from api import Function, functions
import loader

app = FastAPI()

with open('core-manifest.json', 'r') as f:
    VERSION = json.load(f)["version"]


# class NestedCall(BaseModel):
#     id: str | None = None
#     call: str
#     feeds: Dict[str, Any] | None = None

class Conditional(BaseModel):
    AND: List[Conditional] | None = None
    OR: List[Conditional] | None = None
    NOT: Conditional | None = None
    left: str | Any | None = None
    right: str| Any | None = None
    operation: Literal['==', '>', '<', '>=', '<=', 'in'] | None = None

class Loop(BaseModel):
    index: str | None = None
    condition: Conditional | None = None

class Call(BaseModel):
    id: str | None = None
    call: str | None = None
    calls: List[Call] | None = None
    feeds: Dict[str, Any] | None = None
    loop: Loop | None = None
    condition: Conditional | None = None
    initialize: Dict[str, Any] | None = None

class Params(BaseModel):
    api_version: str
    vars: Dict[str, Any] | None = None
    script: List[Call]
    returns: Dict[str, str] = {}

    @field_validator('api_version')
    @classmethod
    def check_version(cls, v):
        if v != VERSION:
            raise ValueError(f'API version mismatch: expected {VERSION}, got {v}')
        return v

class String:
    val: str

    def __init__(self, val: str = None):
        self.val = val

var_pattern = re.compile(r"(?<!\\)(?:\\\\)*\{\{\s*(\^|\.{1,2}|[a-z0-9_]+\.)([a-z0-9_]+)\.?((?:\.?(?:[a-z0-9_]+)|\[\-?\d+\])*)\s*\}\}", re.IGNORECASE)
keypart_re = re.compile(r'\.?([a-z0-9_]+)|\[(\-?\d+)\]', re.IGNORECASE)

MAX_TEMPLATE_DEPTH = 5

def get(_vars, feed: str | Any, last: String, current: str, parent: str):
    if not isinstance(feed, str):
        return feed
    out = feed
    for match in var_pattern.finditer(feed):
        space = match.group(1)
        key = match.group(2)

        if space == '^':
            loc = last.val
        elif space == '.':
            loc = current
        elif space == '..':
            loc = parent
        else:
            loc = space[:-1]

        var = _vars[loc][key]
        # print(f'{loc}.{key} = {var}')
        for m in keypart_re.finditer(match.group(3)):
            if m.group(1) is not None:
                if hasattr(var, '__getitem__'):
                    var = var[m.group(1)]
                else:
                    var = getattr(var, m.group(1))
            else:
                var = var[int(m.group(2))]
        
        if match.span() == (0, len(feed)):
            return var
        else:
            text = match.group(0).strip('\\')
            out = out.replace(text, str(var), 1)
    return out

def resolve_var(value: Any, _vars: Dict[str, Dict[str, Any]], last: String, current: str, parent: str, *, depth: int = 0) -> Any:
    """
    Recursively resolve templates inside value.
    - If value is a string: run the existing template resolution loop (get)
      (which may return a non-string; we keep iterating up to MAX_TEMPLATE_DEPTH).
    - If value is a dict: resolve each value recursively and return a new dict.
    - If value is a list/tuple: resolve each element recursively and return same type.
    - For other types: return as-is.
    """
    if depth > MAX_TEMPLATE_DEPTH:
        # safeguard against pathological recursion
        return value

    # strings -> template resolution loop (your existing behavior)
    if isinstance(value, str):
        out = value
        prev = None
        i = 0
        # iterative resolution for nested templates like "{{ foo[{{ idx }}] }}"
        while (prev is None or prev is not out) and i < MAX_TEMPLATE_DEPTH:
            i += 1
            prev = out
            out = get(_vars, out, last, current, parent)
        return out

    # dict -> resolve each key/value; preserve keys as-is (assume keys are simple strings)
    if isinstance(value, dict):
        result: Dict[str, Any] = {}
        for k, v in value.items():
            result[k] = resolve_var(v, _vars, last, current, parent, depth=depth + 1)
        return result

    # list/tuple -> resolve every element, preserve container type
    if isinstance(value, list):
        return [resolve_var(v, _vars, last, current, parent, depth=depth + 1) for v in value]
    if isinstance(value, tuple):
        return tuple(resolve_var(v, _vars, last, current, parent, depth=depth + 1) for v in value)

    # other types (int, float, bool, None, objects) -> leave as-is
    return value

def resolve_condition(condition: Conditional, _vars: Dict[str, Dict[str, Any]], last: String, current: str, parent: str) -> bool:
    if condition is None: return True

    if condition.AND is not None:        
        for next in condition.AND:
            if not resolve_condition(next, _vars, last, current, parent):
                return False
    elif condition.OR is not None:
        for next in condition.OR:
            if resolve_condition(next, _vars, last, current, parent):
                return True
        return False
    elif condition.NOT is not None:
        return not resolve_condition(condition.NOT, _vars, current, last, parent)
    else:
        if condition.right is None:
            return resolve_var(condition.left, _vars, last, current, parent)
        else:
            left = resolve_var(condition.left, _vars, last, current, parent)
            right = resolve_var(condition.right, _vars, last, current, parent)
            match condition.operation:
                case '==':
                    return left == right
                case '<':
                    return left < right
                case '>':
                    return left > right
                case '<=':
                    return left <= right
                case '>=':
                    return left >= right
                case 'in':
                    return left in right
                case _:
                    return False
                
def direct_call(func: Call, _vars: Dict[str, Dict[str, Any]], last: String, parent: str, called: List[Function]):
    if func.calls is not None:
        for call in func.calls:
            resolve_func(call, _vars, last, func.id, called)
    else:
        execute(func, _vars, last, parent, called)

def resolve_func(func: Call, _vars: Dict[str, Dict[str, Any]], last: String, parent: str, called: List[Function]):
    if not resolve_condition(func.condition, _vars, last, func.id, parent): return

    func.id = func.id or str(uuid.uuid4())
    if func.id not in _vars:
        _vars[func.id] = {}
    if func.initialize is not None:
        for k, v in func.initialize.items():
            if k not in _vars[func.id]:
                _vars[func.id][k] = resolve_var(v, _vars, last, func.id, parent)
    if func.loop is not None:
        indexed = func.loop.index is not None
        if indexed:
            _vars[func.id][func.loop.index] = 0
        
        while resolve_condition(func.loop.condition, _vars, last, func.id, parent):
            direct_call(func, _vars, last, parent, called)
            
            if indexed:
                _vars[func.id][func.loop.index] += 1
    else:
        direct_call(func, _vars, last, parent, called)

def execute(call: Call, _vars: Dict[str, Dict[str, Any]], last: String, parent: str, called: List[Function]):
    nxt = functions[call.call]
    
    params = {}
    for fkey, feed in call.feeds.items():
        params[fkey] = resolve_var(feed, _vars, last, call.id, parent)

    nxt = nxt(params=nxt.parse_params(params))
    
    nxt.exec()
    if nxt.returns is not None:
        for key, val in nxt.returns:
            _vars[call.id][key] = val

    called.append(nxt)
    last.val = call.id

@app.post('/run')
def run(data: Params):
    called: List[Function] = []
    _vars: Dict[str, Dict[str, Any]] = {}

    _vars['vars'] = {}
    for key, var in (data.vars or {}).items():
        _vars['vars'][key] = var

    last = String()
    for func in data.script:
        resolve_func(func, _vars, last, None, called)

    returns: dict[str, Any] = {}

    for name, script in data.returns.items():
        returns[name] = resolve_var(script, _vars, last, None, None)

    
    adapter = TypeAdapter(dict[str, Any])
    safe_returns = adapter.dump_python(returns, mode="json", serialize_as_any=True)
    
    for func in called:
        func.cleanup()

    return JSONResponse(safe_returns)

class FreezeParams(BaseModel):
    functions: List[str] | None = None

@app.post('/freeze')
def freeze(data: FreezeParams):
    if data.functions is not None:
        for fn in data.functions:
            if fn in functions and hasattr(functions[fn], "freeze"):
                functions[fn].freeze()
    else:
        for fn in functions:
            if hasattr(fn, "freeze"):
                functions[fn].freeze()

    return JSONResponse({"status": "success"})

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Run API script or warmup functions.")
    parser.add_argument(
        "--warmup",
        action="store_true",
        help="Run the warmup() method of all registered functions."
    )
    args = parser.parse_args()

    if args.warmup:
        print("Running warmup for all functions...")
        for fn_name, fn_obj in functions.items():
            if hasattr(fn_obj, "warmup"):
                print(f"Warmup: {fn_name}")
                try:
                    fn_obj.warmup()
                except Exception as e:
                    print(f"Error during warmup of {fn_name}: {e}")
        print("Warmup complete.")