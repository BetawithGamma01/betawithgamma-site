#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def main() -> int:
    manifest_path = ROOT / "artifacts/release/release_manifest.json"
    sha_path = ROOT / "artifacts/release/release_manifest.sha256"

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    expected_manifest_hash = sha_path.read_text(encoding="utf-8").strip().split()[0]
    actual_manifest_hash = sha256_file(manifest_path)

    if expected_manifest_hash != actual_manifest_hash:
        print("FAIL: manifest hash mismatch")
        return 1

    for rel, expected in manifest.get("files", {}).items():
        actual = sha256_file(ROOT / rel)
        if actual != expected:
            print(f"FAIL: file hash mismatch for {rel}")
            return 1

    print("PASS: release manifest verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
