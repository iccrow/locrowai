from pathlib import Path
import hashlib
import base64
import json
import zipfile
from tqdm import tqdm
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import load_pem_private_key

# --- Configuration ---
HASH_EXTENSIONS = ( ".py", ".pyd", ".so", ".dll", ".dylib", ".pyx", ".pxd", ".exe", ".c", ".cpp", ".cu", ".h", ".hpp", ".lua", ".bin", ".bat", ".sh" )

PRIVATE_KEY = Path("keys/private.pem")
WHEELS_FOLDER = Path("wheels")
MANIFESTS_FOLDER = Path("wheel_manifests")
MANIFESTS_FOLDER.mkdir(exist_ok=True)

# --- Helper Functions ---
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
    sig_out.write_bytes(base64.b64encode(sig))
    print(f"Signed {manifest_path.name} -> {sig_out.name}")

# --- Main ---
for wheel_file in tqdm(WHEELS_FOLDER.glob("*.whl"), desc="Processing wheels"):
    print(f"\nProcessing {wheel_file.name}")
    wheel_hashes = {}

    with zipfile.ZipFile(wheel_file, "r") as zf:
        for file_info in zf.infolist():
            filename = file_info.filename
            if filename.endswith(HASH_EXTENSIONS):
                with zf.open(file_info) as f:
                    data = f.read()
                    wheel_hashes[filename] = sha256_bytes(data)
                # else:
            #     wheel_hashes[filename] = None  # track other files as None

    # Save manifest
    manifest = {
        "id": wheel_file.name,
        "hashes": wheel_hashes
    }

    manifest_path = MANIFESTS_FOLDER / f"{wheel_file.stem}-manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=4))
    print(f"Manifest created: {manifest_path.name}")

    # Sign manifest
    # sign_manifest(manifest_path, manifest_path.with_suffix(".json.sig.b64"))
