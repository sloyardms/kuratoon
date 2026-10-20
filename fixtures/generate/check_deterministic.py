#!/usr/bin/env python3
"""Generate every implemented case twice into temp dirs and compare SHA-256 hashes of all files.

Exits 0 when both runs are byte-identical, 1 otherwise. CI runs this.

Usage:
    python fixtures/generate/check_deterministic.py
"""

from __future__ import annotations

import hashlib
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import generate  # noqa: E402


def hash_tree(root: Path) -> dict[str, str]:
    return {
        path.relative_to(root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(root.rglob("*"))
        if path.is_file()
    }


def main() -> int:
    names = generate.implemented_cases()
    with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
        generate.generate(names, Path(a))
        generate.generate(names, Path(b))
        first, second = hash_tree(Path(a)), hash_tree(Path(b))

    if not first:
        print("FAIL: generator produced no files", file=sys.stderr)
        return 1
    if first != second:
        for path in sorted(first.keys() | second.keys()):
            if first.get(path) != second.get(path):
                print(f"FAIL: differs: {path}", file=sys.stderr)
        return 1
    print(f"OK: {len(first)} files identical across two runs ({', '.join(names)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
