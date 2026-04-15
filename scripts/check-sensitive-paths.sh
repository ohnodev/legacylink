#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

echo "Scanning for sensitive usernames/absolute local paths..."

python3 - <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(".")
pattern = re.compile(r"/Users/[^/]+/|/home/[^/]+/|[A-Za-z]:\\\\Users\\\\[^\\\\]+\\\\", re.IGNORECASE)
skip_suffixes = {".jar", ".png", ".jpg", ".jpeg", ".gif", ".webp"}
skip_files = {"scripts/check-sensitive-paths.sh"}

hits = []
for path in root.rglob("*"):
    if not path.is_file():
        continue
    rel = path.as_posix()
    if rel.startswith(".git/") or rel in skip_files or path.suffix.lower() in skip_suffixes:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except Exception:
        continue
    for line_no, line in enumerate(text.splitlines(), start=1):
        if pattern.search(line):
            hits.append((rel, line_no, line.strip()))

if hits:
    for rel, line_no, line in hits:
        print(f"{rel}:{line_no}:{line}")
    print("\nERROR: Sensitive identifier/path found. Sanitize before commit/PR.")
    sys.exit(1)

print("No sensitive identifiers/paths detected.")
PY
