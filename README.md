# UWAPO Desktop Fix Package

## What it changes
1. **Scrollbars (desktop):** draggable scrollbar on the Term report grid, batch members list,
   All Members Directory, Finance list, Home unpaid-members list, and the member profile
   payment history.
2. **Term report:** member numbers (e.g. "(63)") removed from the on-screen grid and the PDF.
   The term report is now built from per-row lazy items, so scrolling stays smooth.

## Files touched
- `desktopApp/src/jvmMain/kotlin/com/uwapo/desktop/ui/DesktopUWAPORoot.kt`
- `desktopApp/src/jvmMain/kotlin/com/uwapo/desktop/pdf/DesktopPdfGenerator.kt`

## Apply (pick one)
- **Script (Windows/Mac/Linux):** `python apply_fix.py C:\path\to\UWAPO_Android_Project`
- **Git:** from the repo root, `git apply desktop-scrollbar-term-report.patch`
- **Manual:** copy the two files from `files/` over the originals.

## Verify
1. `./gradlew :desktopApp:run`
2. Reports > Term Report: names show without numbers, scrollbar on the right is draggable.
3. Generate the A4 Landscape PDF: no numbers after names.
4. Members tab: scrollbar works in both Batches & All Members.

## Not changed
Member numbers still show on the Home unpaid list, member lists and profile dialog title.
The "N Members" count in the term report header and the row 1,2,3 column in the PDF remain.

Built on commit 04dde17. Not compiled in the build environment (no Gradle/Android SDK), so run step 1 first.
