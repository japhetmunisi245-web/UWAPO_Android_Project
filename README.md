# UWAPO Batch Year Isolation Fix

This ZIP is a **patch package**, not the complete Android project and not a prebuilt APK.

## What it fixes

### 1. Batch 2026 showing 2025 members/contributions
The Term overview and Term details were filtering payments against all member IDs in the database. The patch restricts members, totals, and paid/partial/pending counts to members whose `batchYear` matches the selected batch.

### 2. A ledger without a year silently becoming the current year
The matrix Excel importer previously fell back to `LocalDate.now().year` when it could not detect a year in the spreadsheet's first rows. A 2025 ledger without a visible year could therefore be imported as 2026 when using the general import action. The patch changes this to a clear error instead of silently assigning the current year.

## Apply on Windows

1. Extract this ZIP.
2. Open PowerShell in the extracted folder.
3. Run the script with your project folder path:

```powershell
python .\apply_batch_year_fix.py "C:\Users\JEFF\OneDrive\Documents\UWAPO_Android_Project"
```

The script creates backup files beside both Kotlin files and refuses to overwrite existing backups.

## Correct import workflow

- Select Batch **2025**, then use that batch's import action with the 2025 workbook.
- Select Batch **2026**, then use that batch's import action with the 2026 workbook.
- Do not import a 2025 workbook into the 2026 batch.
- Export/back up current records before re-importing. This patch does not automatically delete or correct records already imported into the wrong year.

## Build

In PowerShell at the project root:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is normally created at:

`app\build\outputs\apk\debug\app-debug.apk`

Compilation has not been run by this patch package; build and test it locally before using it for official contribution records.
