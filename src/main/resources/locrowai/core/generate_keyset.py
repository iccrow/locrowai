from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.backends import default_backend
import os

# Generate RSA private key
private_key = rsa.generate_private_key(
    public_exponent=65537,
    key_size=4096,  # you can use 4096 for stronger security
    backend=default_backend()
)

# Derive the public key
public_key = private_key.public_key()

# Serialize private key to PEM format
private_pem = private_key.private_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PrivateFormat.TraditionalOpenSSL,  # or PKCS8
    encryption_algorithm=serialization.NoEncryption()        # or use BestAvailableEncryption(b"password")
)

# Serialize public key to PEM format
public_pem = public_key.public_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PublicFormat.SubjectPublicKeyInfo
)

os.makedirs("keys", exist_ok=True)

# Save keys to files
with open("keys/private.pem", "wb") as f:
    f.write(private_pem)

with open("keys/public.pem", "wb") as f:
    f.write(public_pem)

print("RSA key pair generated:")
print(" - keys/private.pem")
print(" - keys/public.pem")
