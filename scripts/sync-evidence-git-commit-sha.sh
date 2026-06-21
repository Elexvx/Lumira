#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_SHA="${LUMIRA_EVIDENCE_TARGET_SHA:-$(git -C "${ROOT_DIR}" rev-parse HEAD)}"
ARTIFACTS_ROOT="${1:-artifacts}"

if ! printf '%s' "${TARGET_SHA}" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "Invalid evidence target commit SHA: ${TARGET_SHA}" >&2
  exit 2
fi

python3 - "$ROOT_DIR" "$TARGET_SHA" "$ARTIFACTS_ROOT" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
target = sys.argv[2]
artifacts_root = root / sys.argv[3]
updated = []

if not artifacts_root.exists():
    raise SystemExit(f"Artifacts path not found: {artifacts_root}")

for path in artifacts_root.rglob("*.json"):
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
    except json.JSONDecodeError:
        continue
    if not isinstance(data, dict) or "gitCommitSha" not in data:
        continue
    if data.get("gitCommitSha") == target and data.get("gitCommitShaMeaning") == "verified-release-target":
        continue
    data["gitCommitSha"] = target
    data["gitCommitShaMeaning"] = "verified-release-target"
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    updated.append(str(path.relative_to(root)))

print(json.dumps({
    "targetSha": target,
    "gitCommitShaMeaning": "verified-release-target",
    "updatedFiles": updated,
    "updatedFileCount": len(updated),
}, indent=2))
PY
