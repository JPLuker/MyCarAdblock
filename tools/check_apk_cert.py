#!/usr/bin/env python3
"""Compare the ACTUAL APK Signing Block certificates across base + feature APKs.

The previous check parsed apksigner text and incorrectly approved a base APK
signed by AGP's implicit debug key alongside a bundletool split signed by a
second debug key. Android then rejected MODE_INHERIT_EXISTING with:
"Existing signatures are inconsistent".

Run apksigner verify separately: this check compares each verified APK's DER
signing certificate(s), which is what Android's package manager compares.
Only APK Signature Scheme v2/v3 is supported, as required on our minSdk 31.
"""
import hashlib
import os
import pathlib
import struct
import sys

MAGIC = b"APK Sig Block 42"
V2 = 0x7109871A
V3 = 0xF05368C0


def u32(data, index):
    if index < 0 or index + 4 > len(data):
        raise ValueError("Truncated APK signing field")
    return struct.unpack_from("<I", data, index)[0]


def u64(data, index):
    if index < 0 or index + 8 > len(data):
        raise ValueError("Truncated APK signing field")
    return struct.unpack_from("<Q", data, index)[0]


def field(data, index):
    length = u32(data, index)
    end = index + 4 + length
    if end > len(data):
        raise ValueError("APK signing field exceeds block length")
    return data[index + 4:end], end


def signing_blocks(apk):
    data = pathlib.Path(apk).read_bytes()
    end_record = data.rfind(b"PK\x05\x06")
    if end_record < 0 or end_record + 22 > len(data):
        raise ValueError("APK ZIP directory missing")
    central_directory = u32(data, end_record + 16)
    if data[central_directory - 16:central_directory] != MAGIC:
        raise ValueError("APK v2/v3 signing block missing")
    size = u64(data, central_directory - 24)
    block_start = central_directory - size - 8
    if block_start < 0 or u64(data, block_start) != size:
        raise ValueError("APK signing block sizes differ")
    pos = block_start + 8
    pairs = {}
    while pos < central_directory - 24:
        length = u64(data, pos)
        end = pos + 8 + length
        if length < 4 or end > central_directory - 24:
            raise ValueError("Malformed signing block entry")
        identifier = u32(data, pos + 8)
        if identifier in pairs:
            raise ValueError("Duplicate APK signing block identifier")
        pairs[identifier] = data[pos + 12:end]
        pos = end
    if pos != central_directory - 24:
        raise ValueError("APK signing block alignment error")
    return pairs


def certificate_digest(scheme):
    signers, remainder = field(scheme, 0)
    if remainder != len(scheme):
        raise ValueError("Trailing signer data")
    signer, after_signer = field(signers, 0)
    if after_signer != len(signers):
        raise ValueError("Multiple APK signing certificates not supported")
    signed_data, _ = field(signer, 0)
    _, offset = field(signed_data, 0)  # Signature digests
    certificates, _ = field(signed_data, offset)
    certificate, after_certificate = field(certificates, 0)
    if after_certificate != len(certificates) or not certificate:
        raise ValueError("Unexpected APK certificate chain")
    return hashlib.sha256(certificate).hexdigest()


def actual_certificate(apk):
    schemes = signing_blocks(apk)
    if V2 not in schemes:
        raise ValueError("Required APK Signature Scheme v2 missing")
    digest = certificate_digest(schemes[V2])
    if V3 in schemes and certificate_digest(schemes[V3]) != digest:
        raise ValueError("APK v2/v3 signers differ")
    return digest


def check(apks):
    pairs = [(path, actual_certificate(path)) for path in apks]
    for path, digest in pairs:
        print(f"{path}: cert sha256 {digest}")
    if len({digest for _, digest in pairs}) != 1:
        raise ValueError("Base APK and optional GeckoView splits have DIFFERENT signing certificates")
    # The shared signer check is necessary but not sufficient: all APKs could have
    # been signed with the wrong key. Pin the expected trusted certificate in CI.
    expected = os.environ.get("MYCAR_EXPECT_CERT_SHA256", "").strip().lower()
    if expected:
        if len(expected) != 64 or any(c not in "0123456789abcdef" for c in expected):
            raise ValueError("Configured signing certificate fingerprint is malformed")
        if pairs[0][1] != expected:
            raise ValueError("APK signing certificate differs from the configured trusted key")


def main():
    if len(sys.argv) < 3:
        print("Usage: check_apk_cert.py base.apk split-master.apk [more-splits.apk...]", file=sys.stderr)
        raise SystemExit(2)
    try:
        check([pathlib.Path(arg) for arg in sys.argv[1:]])
    except (ValueError, OSError, struct.error) as error:
        print(f"Certificate check FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
    print("Certificate check passed: base and all optional splits match exactly")


if __name__ == "__main__":
    main()
