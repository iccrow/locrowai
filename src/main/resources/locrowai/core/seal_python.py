from pathlib import Path
import hashlib
import base64
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_pem_private_key
import json
from tqdm import tqdm

BLACKLIST = (
    ".pyc"
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

core = Path("python").rglob("*")

manifest_path = Path("python-manifest.json")
_hashes = {}

for file in tqdm(core):
    if file.is_dir() or file == manifest_path or file == manifest_path.with_suffix(".json.sig.b64"):
        continue
    key = file.relative_to(Path("python")).as_posix()
    if not file.suffix in BLACKLIST:
        print(f"Hashing {file}")
        with file.open("rb") as f:
            data = f.read()
            file_hash = sha256_bytes(data)
            _hashes[key] = file_hash

with manifest_path.open("w") as manifest_file:
    content = {"id": "python", "verson": "3.10.19", "description": "Python 3.10 runtime", "hashes": _hashes}
    json.dump(content, manifest_file, indent=4)

# sign_manifest(manifest_path, manifest_path.with_suffix(".json.sig.b64"))