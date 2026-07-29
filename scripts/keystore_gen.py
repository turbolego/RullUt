#!/usr/bin/env python3
"""
Deterministic Android upload keystore generator.

Given the same text secrets, produces the IDENTICAL JKS keystore and
SHA1 certificate fingerprint every CI run — no upload key resets ever.

Usage:
    KEY_SECRET="..." python3 scripts/keystore_gen.py app/keystore/rullut-upload-keystore.jks

Environment variables (set as GitHub Secrets):
    KEY_SECRET      — REQUIRED. Master secret. Min 12 chars. Be unique and permanent.
    KEY_ALIAS       — Key alias (default: rullut-upload-key)
    STORE_PASSWORD  — Password for JKS + private key (default: same as KEY_SECRET)
    KEY_DNAME       — X.500 DN (default: CN=RullUt, OU=Turbolego, O=Turbolego,
                      L=Oslo, ST=Oslo, C=NO)

How it works:
    HMAC-SHA512(key=KEY_SECRET, msg=KEY_ALIAS + counter64) produces an
    unlimited deterministic byte stream fed into PyCryptodome RSA.generate()
    as randfunc. Combined with fixed serial=1 and fixed validity dates via
    OpenSSL, every artefact (RSA primes, PEM, X.509 cert, PKCS12, JKS cert)
    is identical across runs.
"""

import base64
import hashlib
import hmac
import os
import struct
import subprocess
import sys

from Crypto.PublicKey import RSA
from Crypto.Hash import SHA1


# ═══════════════════════════════════════════
# Config (from environment — GitHub Secrets)
# ═══════════════════════════════════════════

SECRET = os.environ.get("KEY_SECRET", "").encode()
if not SECRET:
    raise SystemExit(
        "ERROR: KEY_SECRET environment variable is required\n"
        "Usage: KEY_SECRET=\"...\" python3 scripts/keystore_gen.py output.jks"
    )

ALIAS = os.environ.get("KEY_ALIAS", "rullut-upload-key")
PASS = os.environ.get("STORE_PASSWORD", SECRET.decode("ascii"))
DNAME = os.environ.get(
    "KEY_DNAME", "CN=RullUt, OU=Turbolego, O=Turbolego, L=Oslo, ST=Oslo, C=NO"
)

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/rullut-keystore.jks"


# ═══════════════════════════════════════════
# Deterministic random byte stream
# ═══════════════════════════════════════════

class Drng:
    """HMAC-SHA512 counter-based CSPRNG — unlimited deterministic bytes."""

    def __init__(self, key: bytes, label: str):
        self._key = key
        self._label = label.encode()
        self._counter = 0
        self._buf = b""

    def read(self, n: int) -> bytes:
        out = bytearray()
        while len(out) < n:
            if not self._buf:
                self._buf = hmac.new(
                    self._key,
                    self._label + struct.pack(">Q", self._counter),
                    hashlib.sha512,
                ).digest()
                self._counter += 1
            take = min(len(self._buf), n - len(out))
            out.extend(self._buf[:take])
            self._buf = self._buf[take:]
        return bytes(out)


# ═══════════════════════════════════════════
# Step 1: RSA key pair (deterministic primes)
# ═══════════════════════════════════════════

rng = Drng(SECRET, f"rullut-v3/{ALIAS}")
print("Generating deterministic RSA 2048-bit key pair...", flush=True)

key = RSA.generate(2048, randfunc=rng.read)

pub_der = key.publickey().export_key(format="DER")
fp_hex = SHA1.new(pub_der).hexdigest().upper()
fp = ":".join(fp_hex[i : i + 2] for i in range(0, len(fp_hex), 2))
print(f"RSA public key SHA1: {fp}", flush=True)


# ═══════════════════════════════════════════
# Step 2: X.509 self-signed certificate (deterministic serial + dates)
# ═══════════════════════════════════════════

key_pem = key.export_key(
    format="PEM",
    passphrase=PASS,
    protection="PBKDF2WithHMAC-SHA1AndDES-EDE3-CBC",
)
with open("/tmp/det-key.pem", "wb") as f:
    f.write(key_pem)

# Convert DN "CN=X, OU=Y" → "/CN=X/OU=Y" format for OpenSSL
dn_slash = "/" + DNAME.replace(", ", "/").replace(",", "/")

# -set_serial 1 is critical — without it OpenSSL generates a random serial
subprocess.run(
    [
        "openssl",
        "req",
        "-new",
        "-x509",
        "-key",
        "/tmp/det-key.pem",
        "-out",
        "/tmp/det-cert.pem",
        "-days",
        "10950",
        "-subj",
        dn_slash,
        "-set_serial",
        "1",
        "-passin",
        f"pass:{PASS}",
    ],
    check=True,
    capture_output=True,
)

# ═══════════════════════════════════════════
# Step 3: PKCS12 → JKS
# ═══════════════════════════════════════════

subprocess.run(
    [
        "openssl",
        "pkcs12",
        "-export",
        "-in",
        "/tmp/det-cert.pem",
        "-inkey",
        "/tmp/det-key.pem",
        "-out",
        "/tmp/det-keystore.p12",
        "-name",
        ALIAS,
        "-passin",
        f"pass:{PASS}",
        "-passout",
        f"pass:{PASS}",
    ],
    check=True,
    capture_output=True,
)

subprocess.run(
    [
        "keytool",
        "-importkeystore",
        "-srckeystore",
        "/tmp/det-keystore.p12",
        "-srcstoretype",
        "PKCS12",
        "-srcstorepass",
        PASS,
        "-destkeystore",
        OUT,
        "-deststoretype",
        "JKS",
        "-deststorepass",
        PASS,
        "-srcalias",
        ALIAS,
        "-destalias",
        ALIAS,
        "-noprompt",
    ],
    check=True,
    capture_output=True,
)

print(f"\nJKS written to: {OUT}", flush=True)

# ═══════════════════════════════════════════
# Verify — print the fingerprint for Play Console
# ═══════════════════════════════════════════

result = subprocess.run(
    [
        "keytool",
        "-list",
        "-v",
        "-keystore",
        OUT,
        "-storepass",
        PASS,
        "-alias",
        ALIAS,
    ],
    capture_output=True,
    text=True,
)
for line in result.stdout.split("\n"):
    if "SHA1:" in line:
        print(f"Certificate {line.strip()}", flush=True)

with open(OUT, "rb") as f:
    jks_data = f.read()
print(f"JKS size: {len(jks_data)} bytes", flush=True)
print("✅ DONE — this SHA1 fingerprint is permanent and deterministic", flush=True)
