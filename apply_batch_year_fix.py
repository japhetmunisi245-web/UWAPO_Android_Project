#!/usr/bin/env python3
"""
Apply UWAPO batch-year isolation fixes to a local project checkout.

Usage:
  python apply_batch_year_fix.py "C:\\path\\to\\UWAPO_Android_Project"

The script:
- isolates Term totals and member lists to the selected batch year;
- prevents a matrix Excel file with no detectable year from silently importing
  into the current year (e.g. a 2025 ledger accidentally becoming 2026);
- creates backups and refuses to edit if expected source patterns differ.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    print('Usage: python apply_batch_year_fix.py "PATH_TO_UWAPO_Android_Project"')
    raise SystemExit(2)

root = Path(sys.argv[1]).expanduser().resolve()
ui = root / "app/src/main/java/com/uwapo/app/ui/UWAPORoot.kt"
imp = root / "app/src/main/java/com/uwapo/app/data/CsvImportExport.kt"

for p in (ui, imp):
    if not p.is_file():
        print(f"ERROR: Expected source file not found: {p}")
        print("No files were changed. Point the script to the project root.")
        raise SystemExit(1)

ui_text = ui.read_text(encoding="utf-8")
imp_text = imp.read_text(encoding="utf-8")

# Each batch period screen must only use members assigned to the selected batch.
old_ids = "val batchMemberIds = members.map { it.id }.toSet()"
new_ids = """val batchMemberIds = members
            .filter { it.batchYear == batchYear.year }
            .map { it.id }
            .toSet()"""
ids_count = ui_text.count(old_ids)
if ids_count != 2:
    print(f"ERROR: Expected 2 unscoped batchMemberIds filters in UWAPORoot.kt; found {ids_count}.")
    print("No files were changed. Source may differ from the version this patch targets.")
    raise SystemExit(1)

old_eligible = """val eligibleMembers = remember(members) {
        members.filter { it.status == "active" }
    }"""
new_eligible = """val eligibleMembers = remember(members, batchYear) {
        members.filter { it.batchYear == batchYear.year && it.status == "active" }
    }"""
eligible_count = ui_text.count(old_eligible)
if eligible_count != 1:
    print(f"ERROR: Expected 1 eligibleMembers filter in UWAPORoot.kt; found {eligible_count}.")
    print("No files were changed. Source may differ from the version this patch targets.")
    raise SystemExit(1)

# Period overview counts must also be calculated from this batch's active members.
old_count = "val paidCount = activeMembers.count { m ->"
new_count = "val activeBatchMembers = members.filter { it.batchYear == batchYear.year && it.status == \"active\" }\n                val paidCount = activeBatchMembers.count { m ->"
if ui_text.count(old_count) != 1:
    print("ERROR: Could not safely locate the period paidCount calculation. No files were changed.")
    raise SystemExit(1)
ui_new = ui_text.replace(old_ids, new_ids)
ui_new = ui_new.replace(old_eligible, new_eligible)
ui_new = ui_new.replace(old_count, new_count)
ui_new = ui_new.replace("val partialCount = activeMembers.count { m ->", "val partialCount = activeBatchMembers.count { m ->")
ui_new = ui_new.replace("val pendingCount = activeMembers.count { m ->", "val pendingCount = activeBatchMembers.count { m ->")

# If no year can be detected in a matrix ledger, never silently use the current year.
old_detect = """        return LocalDate.now().year
    }

    private fun parseMemberCell"""
new_detect = """        throw IllegalArgumentException(
            "Could not detect the contribution year in this Excel file. Open the intended Batch (for example, 2025 or 2026) and import the ledger from that Batch so its year is explicit."
        )
    }

    private fun parseMemberCell"""
if imp_text.count(old_detect) != 1:
    print("ERROR: Could not safely locate detectYear() fallback in CsvImportExport.kt. No files were changed.")
    raise SystemExit(1)
imp_new = imp_text.replace(old_detect, new_detect)

# Back up both files first; refuse to overwrite existing backups.
ui_bak = ui.with_suffix(ui.suffix + ".batch-year-fix.bak")
imp_bak = imp.with_suffix(imp.suffix + ".batch-year-fix.bak")
if ui_bak.exists() or imp_bak.exists():
    print("ERROR: A .batch-year-fix.bak backup already exists. Rename/remove it after preserving it, then rerun.")
    raise SystemExit(1)

ui_bak.write_text(ui_text, encoding="utf-8")
imp_bak.write_text(imp_text, encoding="utf-8")
ui.write_text(ui_new, encoding="utf-8")
imp.write_text(imp_new, encoding="utf-8")

print("SUCCESS: UWAPO batch-year isolation fixes applied.")
print(f"Backup: {ui_bak}")
print(f"Backup: {imp_bak}")
print("Next: build the project and verify 2025 and 2026 independently.")
