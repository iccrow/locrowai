import ast
import operator as _operator
from typing import Dict, Optional, Any

from pydantic import BaseModel, ConfigDict

from api import Function, register

# === Params / Returns models ===

class EvalParams(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    expr: str
    vars: Optional[Dict[str, float]] = None  # optional numeric variables

class EvalReturns(BaseModel):
    result: float

# === Allowed operators (only the set you requested) ===
_ALLOWED_BINOPS = {
    ast.Add: _operator.add,         # +
    ast.Sub: _operator.sub,         # -
    ast.Mult: _operator.mul,        # *
    ast.Div: _operator.truediv,     # /
    ast.Mod: _operator.mod,         # %
    ast.Pow: _operator.pow,         # **
    ast.FloorDiv: _operator.floordiv,  # //
    ast.MatMult: _operator.matmul,  # @
}

_ALLOWED_UNARYOPS = {
    ast.UAdd: _operator.pos,   # unary +
    ast.USub: _operator.neg,   # unary -
}


def _safe_eval_node(node: ast.AST, names: Dict[str, Any]) -> Any:
    """
    Evaluate AST nodes but only allow:
    - numeric constants (int/float)
    - variables provided in 'names' (must be int/float)
    - binary ops: + - * / ** // % @
    - unary + and - 
    - parentheses (sub-expressions)
    Nothing else is permitted (no function calls, no attribute access, no names other than allowed variables).
    """
    if isinstance(node, ast.Expression):
        return _safe_eval_node(node.body, names)

    # Python 3.8+: numeric constants are ast.Constant
    if isinstance(node, ast.Constant):
        if isinstance(node.value, (int, float)):
            return node.value
        raise ValueError(f"Unsupported constant type: {type(node.value).__name__}")

    # Older ast.Num compatibility
    if isinstance(node, ast.Num):
        return node.n

    # Binary operations
    if isinstance(node, ast.BinOp):
        op_type = type(node.op)
        if op_type not in _ALLOWED_BINOPS:
            raise ValueError(f"Disallowed binary operator: {op_type.__name__}")
        left = _safe_eval_node(node.left, names)
        right = _safe_eval_node(node.right, names)
        # ensure numeric types for operands for safety (allow int/float)
        if not isinstance(left, (int, float)) or not isinstance(right, (int, float)):
            # allow @ (matmul) to defer to python operator for types that implement matmul
            if op_type is ast.MatMult:
                return _ALLOWED_BINOPS[op_type](left, right)
            raise ValueError("Operands must be numeric for arithmetic operators")
        return _ALLOWED_BINOPS[op_type](left, right)

    # Unary ops (+,-)
    if isinstance(node, ast.UnaryOp):
        op_type = type(node.op)
        if op_type not in _ALLOWED_UNARYOPS:
            raise ValueError(f"Disallowed unary operator: {op_type.__name__}")
        operand = _safe_eval_node(node.operand, names)
        if not isinstance(operand, (int, float)):
            raise ValueError("Unary operand must be numeric")
        return _ALLOWED_UNARYOPS[op_type](operand)

    # Parentheses and grouped expressions are just nested nodes, so nothing special required.
    # Allow tuple/parenthesis as expression grouping (accept a single element tuple only if present)
    if isinstance(node, ast.Tuple):
        if len(node.elts) == 1:
            return _safe_eval_node(node.elts[0], names)
        raise ValueError("Tuples are not supported in expressions")

    # Names: allow only those provided in `names` and they must be numeric
    if isinstance(node, ast.Name):
        id_ = node.id
        if id_ in names:
            val = names[id_]
            if not isinstance(val, (int, float)):
                raise ValueError(f"Variable '{id_}' must be numeric")
            return val
        raise ValueError(f"Unknown identifier: {id_}")

    # Subscript, Call, Attribute, etc. are not permitted
    raise ValueError(f"Disallowed expression node: {type(node).__name__}")


@register("/math/eval")
class MathEvalFunc(Function[EvalParams, EvalReturns]):

    def exec(self):
        expr = (self.params.expr or "").strip()
        if not expr:
            raise ValueError("Empty expression")

        # Build allowed names from provided vars (variables must be numeric)
        names: Dict[str, Any] = {}
        if self.params.vars:
            for k, v in self.params.vars.items():
                if not isinstance(v, (int, float)):
                    raise ValueError(f"Variable '{k}' must be numeric")
                names[k] = v

        # Parse to AST in 'eval' mode
        try:
            parsed = ast.parse(expr, mode="eval")
        except SyntaxError as e:
            raise ValueError(f"Invalid expression syntax: {e}")

        # Quick scan to reject unsafe node types early
        for node in ast.walk(parsed):
            # disallow dangerous constructs explicitly
            if isinstance(node, (ast.Import, ast.ImportFrom, ast.Call,
                                 ast.Attribute, ast.Lambda, ast.ListComp,
                                 ast.SetComp, ast.DictComp, ast.GeneratorExp,
                                 ast.Global, ast.Nonlocal, ast.IfExp)):
                raise ValueError(f"Disallowed expression construct: {type(node).__name__}")

            # disallow boolean ops, comparisons, etc. — only arithmetic nodes allowed
            if isinstance(node, (ast.BoolOp, ast.Compare)):
                raise ValueError("Boolean operations and comparisons are not allowed in this evaluator")

        # Evaluate safely
        result = _safe_eval_node(parsed, names)

        if not isinstance(result, (int, float)):
            raise ValueError("Expression did not evaluate to a numeric result")

        self.returns = EvalReturns(result=float(result))

    @staticmethod
    def warmup():
        params = EvalParams(expr="1 + 2 * 3")
        func = MathEvalFunc(params=params)
        func.exec()