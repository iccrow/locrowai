# import_tracker.py (extended)
from __future__ import annotations
import builtins
import atexit
import importlib
import importlib.metadata as metadata
import importlib.util
import sys
from typing import Set, List

_original_import = builtins.__import__
_tracked_toplevels: Set[str] = set()

def _record_import(name: str, fromlist) -> None:
    if not name:
        return
    top = name.split(".", 1)[0]
    if top:
        _tracked_toplevels.add(top)
    if fromlist:
        for item in fromlist:
            if isinstance(item, str) and item:
                _tracked_toplevels.add(top)

def _hooked_import(name, globals=None, locals=None, fromlist=(), level=0):
    module = _original_import(name, globals, locals, fromlist, level)
    try:
        # record on every import call (imports call __import__ even if module already loaded)
        if level == 0:
            _record_import(name, fromlist)
        else:
            if hasattr(module, "__name__"):
                _record_import(module.__name__, ())
    except Exception as e:
        pass
    return module

def _is_third_party_package(top_level_name: str) -> bool:
    try:
        spec = importlib.util.find_spec(top_level_name)
        if not spec:
            return False
        origin = getattr(spec, "origin", None)
        if not origin:
            return False
        origin_lower = origin.lower()
        return ("site-packages" in origin_lower) or ("dist-packages" in origin_lower)
    except Exception:
        return False

def _gather_requirements(include_stdlib: bool = False, freeze_exact: bool = True) -> List[str]:
    try:
        pkg_to_dists = metadata.packages_distributions()
    except Exception:
        pkg_to_dists = {}

    reqs: List[str] = []
    seen_dists = set()

    for top in sorted(_tracked_toplevels):
        if not include_stdlib and not _is_third_party_package(top):
            continue

        dist_name = top
        if dist_name not in seen_dists:
            seen_dists.add(dist_name)
            reqs.append(dist_name)

    return reqs

# ---- Public API ----

def start_requirements() -> None:
    """Activate import-tracking (call early)."""
    builtins.__import__ = _hooked_import
    # safe guard to write nothing if program exits and you forgot to write — keep it minimal
    def _noop_on_exit():
        pass
    atexit.register(_noop_on_exit)

def stop_tracking() -> None:
    """Restore original import to stop tracking."""
    builtins.__import__ = _original_import

def clear_tracked() -> None:
    """Clear the set of recorded top-level module names (use between script runs)."""
    _tracked_toplevels.clear()

def get_tracked() -> List[str]:
    """Return sorted list of currently tracked top-level module names."""
    return sorted(_tracked_toplevels)

def get_requirements(include_stdlib: bool = False, freeze_exact: bool = True) -> List[str]:
    """
    Return requirement lines (e.g., ['requests==2.31.0']). Does not write file.
    Useful to inspect or write multiple requirement files without restarting.
    """
    return _gather_requirements(include_stdlib=include_stdlib, freeze_exact=freeze_exact)

def write_requirements(filename: str = "requirements.txt", include_stdlib: bool = False, freeze_exact: bool = True) -> None:
    """Write current requirements to filename now."""
    reqs = get_requirements(include_stdlib=include_stdlib, freeze_exact=freeze_exact)
    try:
        with open(filename, "w", encoding="utf-8") as fh:
            for r in reqs:
                fh.write(r + "\n")
    except Exception as e:
        print(f"[import_tracker] failed to write {filename}: {e}", file=sys.stderr)
