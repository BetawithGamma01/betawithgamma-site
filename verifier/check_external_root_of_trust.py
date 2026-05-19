#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent.parent

VALID_TYPES_TIER1 = {"SIGSTORE_REKOR", "NOTARIZED_HASH_RECORD"}
VALID_TYPES_TIER2 = {"SIGNED_GIT_TAG_WITH_EXTERNAL_KEY", "PROTECTED_RELEASE_ATTESTATION"}
INVALID_TYPES = {
    "LOCAL_KEYPAIR",
    "REPO_ONLY_PUBLIC_KEY",
    "PLACEHOLDER_SIGNATURE",
    "SELF_SIGNED_NO_EXTERNAL_IDENTITY",
    "AGENT_GENERATED_KEY",
}


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def find_private_key_material() -> list[str]:
    hits = []
    blocked = {".git", "node_modules", "venv", ".venv", "__pycache__"}
    needles = ["BEGIN PRIVATE KEY", "BEGIN RSA PRIVATE KEY", "BEGIN EC PRIVATE KEY", "PRIVATE KEY-----"]
    for p in ROOT.rglob("*"):
        if any(part in blocked for part in p.parts):
            continue
        if p.is_file() and p.suffix.lower() in {".pem", ".key", ".p12", ".pfx", ".asc", ".gpg", ".txt", ".md", ".json", ""}:
            try:
                txt = p.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            if any(n in txt for n in needles):
                hits.append(str(p.relative_to(ROOT)))
    return hits


def likely_fetchable(value: str) -> bool:
    pr = urlparse(value)
    if pr.scheme in {"http", "https"} and pr.netloc:
        return True
    return value.startswith("rekor:") or value.startswith("notary:")


def main() -> int:
    pointer = json.loads((ROOT / "external_anchor/EXTERNAL_ROOT_POINTER.json").read_text(encoding="utf-8"))
    manifest_sha_line = (ROOT / "artifacts/release/release_manifest.sha256").read_text(encoding="utf-8").strip()
    manifest_sha = manifest_sha_line.split()[0]
    verifier_sha = sha256_file(ROOT / "verifier/check_external_root_of_trust.py")

    errors = []
    ext_type = pointer.get("external_record_type")
    if ext_type in INVALID_TYPES:
        errors.append("external record type is invalid/local")
    if ext_type not in (VALID_TYPES_TIER1 | VALID_TYPES_TIER2):
        errors.append("external record type is not a valid tier1/tier2 type")

    if not pointer.get("external_record_url_or_id"):
        errors.append("external record is missing")
    elif not likely_fetchable(pointer["external_record_url_or_id"]):
        errors.append("external record cannot be fetched")

    if pointer.get("repo_only_public_key_allowed") is not False:
        errors.append("repo-only public key allowance must be false")

    if pointer.get("self_minted_key_allowed") is not False:
        errors.append("self-minted key allowance must be false")

    pk_loc = pointer.get("public_key_external_location", "")
    if pk_loc.startswith("repo:") or pk_loc.startswith("./") or "github.com" not in pk_loc and pk_loc.startswith("file:"):
        errors.append("signature exists but public key exists only in repo")

    if pointer.get("expected_release_manifest_sha256") != manifest_sha:
        errors.append("release manifest hash differs from external record")

    if pointer.get("expected_external_root_verifier_sha256") != verifier_sha:
        errors.append("verifier hash differs from external record")

    if not pointer.get("external_record_binds_verifier_hash", False):
        errors.append("external record does not bind verifier hash")

    trusted_executor = pointer.get("trusted_executor")
    if pointer.get("external_trust") == "PROVEN" and not trusted_executor:
        errors.append("external_trust=PROVEN but no trusted executor exists")

    if find_private_key_material():
        errors.append("private key exists in repo")

    if errors:
        print("FAIL")
        for e in errors:
            print(f"- {e}")
        return 1

    print("PASS: externally proven")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
