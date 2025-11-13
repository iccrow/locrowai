from pathlib import Path
import hashlib
import base64
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_pem_private_key
import json
from tqdm import tqdm

BLACKLIST = (
    ".pyc",
    "requirements.txt",
    "wheels.txt",
    "python-manifest.json",
    "core-manifest.json",
    "core-manifest.json.sig.b64",
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

core = []

core.extend(list(Path(".").glob("*")))
core.extend(list(Path("sample").glob("*")))
core.extend(list(Path("api").rglob("*")))
print(core)

manifest_path = Path("core-manifest.json")
_hashes = {}

for file in tqdm(core):
    if file.is_dir():
        continue
    key = file.relative_to(Path(".")).as_posix()
    if not file.name.endswith(BLACKLIST):
        print(f"Hashing {file}")
        with file.open("rb") as f:
            data = f.read()
            file_hash = sha256_bytes(data)
            _hashes[key] = file_hash

with manifest_path.open("r+") as manifest_file:
    content = json.load(manifest_file)
    content["hashes"] = _hashes
    manifest_file.seek(0)
    manifest_file.truncate()
    json.dump(content, manifest_file, indent=4)

sign_manifest(manifest_path, manifest_path.with_suffix(".json.sig.b64"))