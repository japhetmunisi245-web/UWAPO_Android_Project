#!/usr/bin/env python3
"""
UWAPO desktop fix package: scrollbars + no member numbers in the term report.

Usage:   python apply_fix.py /path/to/UWAPO_Android_Project [--force]

Safe by default:
  - backs up every file it replaces as <name>.bak
  - if your file differs from the version this fix was built on (you changed it
    since), it does NOT overwrite; it writes <name>.fixed next to it instead.
    Use --force to overwrite anyway, or apply desktop-scrollbar-term-report.patch
    with 'git apply' to merge.
"""
import hashlib, shutil, sys
from pathlib import Path

BASE = "desktopApp/src/jvmMain/kotlin/com/uwapo/desktop"
ORIGINAL_SHA256 = {
    f"{BASE}/ui/DesktopUWAPORoot.kt": "5dd00cdd71b562602da5cf0fde22d01788126e443235a1ec042a31898dae401d",
    f"{BASE}/pdf/DesktopPdfGenerator.kt": "3f6b11e0e4a775e3f2552b23d16df33e36acb1a7339739bad4a147dd38058553",
}

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

args = [a for a in sys.argv[1:] if a != "--force"]
force = "--force" in sys.argv
if len(args) != 1:
    print(__doc__); raise SystemExit(2)

root = Path(args[0]).expanduser().resolve()
here = Path(__file__).resolve().parent / "files"

for rel, orig in ORIGINAL_SHA256.items():
    target, fixed = root / rel, here / rel
    if not target.is_file():
        print(f"ERROR: not found: {target}\nPoint the argument at the UWAPO project root."); raise SystemExit(1)
    if sha(target) == sha(fixed):
        print(f"already applied: {rel}"); continue
    if sha(target) != orig and not force:
        out = target.with_name(target.name + ".fixed")
        shutil.copy2(fixed, out)
        print(f"SKIPPED (your copy was modified): {rel}\n  wrote {out.name} instead; merge it or rerun with --force")
        continue
    shutil.copy2(target, target.with_name(target.name + ".bak"))
    shutil.copy2(fixed, target)
    print(f"patched: {rel}  (backup: {target.name}.bak)")

print("\nNext: ./gradlew :desktopApp:run")
