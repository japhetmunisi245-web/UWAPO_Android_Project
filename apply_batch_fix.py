#!/usr/bin/env python3
"""
Apply the UWAPO Batch totals fix to an existing local checkout.
Run from any folder:
  python apply_batch_fix.py /path/to/UWAPO_Android_Project

This script only changes the two batch-member filters in UWAPORoot.kt.
It creates a .bak backup before editing and fails safely if the expected
source pattern is not found exactly twice.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    print("Usage: python apply_batch_fix.py /path/to/UWAPO_Android_Project")
    raise SystemExit(2)

root = Path(sys.argv[1]).expanduser().resolve()
target = root / "app/src/main/java/com/uwapo/app/ui/UWAPORoot.kt"

if not target.is_file():
    print(f"ERROR: Could not find expected file:\n  {target}")
    print("Make sure the argument points to the root of the UWAPO Android project.")
    raise SystemExit(1)

text = target.read_text(encoding="utf-8")
old = "val batchMemberIds = members.map { it.id }.toSet()"
new = """val batchMemberIds = members
            .filter { it.batchYear == batchYear.year }
            .map { it.id }
            .toSet()"""

count = text.count(old)
if count != 2:
    print(f"ERROR: Expected to find the old filter exactly twice, but found {count}.")
    print("No changes were made. The source may have changed; inspect UWAPORoot.kt manually.")
    raise SystemExit(1)

backup = target.with_suffix(target.suffix + ".bak")
if backup.exists():
    print(f"ERROR: Backup already exists; refusing to overwrite it:\n  {backup}")
    print("Move or rename the existing backup, then rerun.")
    raise SystemExit(1)

backup.write_text(text, encoding="utf-8")
target.write_text(text.replace(old, new), encoding="utf-8")
print("SUCCESS: Updated both batch-specific member filters.")
print(f"Backup created: {backup}")
print("Next: build and test the app; see README.md in this ZIP.")
