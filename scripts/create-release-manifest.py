#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = ROOT / "artifacts/release/release_manifest.json"
SHA_PATH = ROOT / "artifacts/release/release_manifest.sha256"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def main() -> int:
    verifier = ROOT / "verifier/check_external_root_of_trust.py"
    pointer = ROOT / "external_anchor/EXTERNAL_ROOT_POINTER.json"

    manifest = {
        "version": "1.0",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "status": "LOCAL_EXTERNAL_ROOT_PROTOCOL_EXTERNAL_TRUST_NONE",
        "external_trust": "NONE",
        "files": {
            "verifier/check_external_root_of_trust.py": sha256_file(verifier),
            "external_anchor/EXTERNAL_ROOT_POINTER.json": sha256_file(pointer),
        },
    }

    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    digest = sha256_file(MANIFEST_PATH)
    SHA_PATH.write_text(f"{digest}  artifacts/release/release_manifest.json\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
