from pathlib import Path
import hashlib
import base64
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_pem_private_key
import json
import importlib
from tqdm import tqdm

from import_tracker import start_requirements, get_requirements, clear_tracked, stop_tracking
from api.extensions import functions

BLACKLIST = (
    ".pyc",
    "manifest.json",
    "manifest.json.sig.b64",
)

PRIVATE_KEY = Path("keys/private.pem")

def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def sign_manifest(manifest_path: Path, sig_out: Path):
    priv = load_pem_private_key(PRIVATE_KEY.read_bytes(), password=None)
    msg = manifest_path.read_bytes()
    sig = priv.sign(
        msg,
        padding.PSS(mgf=padding.MGF1(hashes.SHA256()), salt_length=padding.PSS.MAX_LENGTH),
        hashes.SHA256()
    )
    # write signature as base64 for easy storage/transport
    sig_out.write_bytes(base64.b64encode(sig))
    print(f"Signed {manifest_path} -> {sig_out}")

extensions = Path("extensions")

start_requirements()

for extension in tqdm(extensions.glob("*/__init__.py")):
    manifest_path = extension.parent / "manifest.json"
    if not manifest_path.exists():
        print(f"Manifest not found for extension {extension.parent.name}, skipping.")
        continue

    importlib.import_module(f'extensions.{extension.parent.name}')
    for func in functions.values():
        print(f"Warmup: {func.__name__}")
        if hasattr(func, "warmup"):
            func.warmup()
    
    functions.clear()

    reqs = sorted(get_requirements())

    clear_tracked()

    _hashes = {}

    for file in extension.parent.rglob("*"):
        if file.is_dir():
            continue
        key = file.relative_to(extension.parent).as_posix()
        if not file.name.endswith(BLACKLIST) and not key.startswith(("models\\", "models/", "cache\\", "cache/")):
            with file.open("rb") as f:
                data = f.read()
                file_hash = sha256_bytes(data)
                _hashes[key] = file_hash

    with manifest_path.open("r+") as manifest_file:
        content = json.load(manifest_file)
        content["hashes"] = _hashes
        content["requirements"] = reqs
        manifest_file.seek(0)
        manifest_file.truncate()
        json.dump(content, manifest_file, indent=4)

    sign_manifest(manifest_path, manifest_path.with_suffix(".json.sig.b64"))

stop_tracking()