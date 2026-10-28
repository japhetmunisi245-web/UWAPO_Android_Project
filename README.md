# UWAPO Android — Local-Only Edition

Native Android membership and contribution manager. **No Supabase, no cloud API, no account sign-in, and no network permissions.** Data is stored in the Room/SQLite database on this Android device.

## Stack
- Kotlin, Jetpack Compose, Material 3
- MVVM-oriented UI and Room persistence
- Local CSV import/export
- Currency: TZS; default monthly contribution: TZS 10,000

## Open and build
1. Install Android Studio, Android SDK 35, and JDK 17.
2. Open this folder in Android Studio.
3. Let Gradle sync and download AndroidX dependencies.
4. Use **Build → Build APK(s)**.
5. Debug APK normally appears at `app/build/outputs/apk/debug/app-debug.apk`.

The source archive has not been compiled in this environment; build and test in Android Studio before relying on it for official records.

## Local data and backups
- Records remain on the device and are not shared with committee members' other devices.
- Use the app's CSV export regularly and store backups securely.
- Uninstalling the app or clearing its data may erase the database. Android device backup is disabled in the manifest.
- There is no cloud account, remote backup, or multi-device sync.

## Features
- Dashboard and local totals
- Member register and CSV import/export
- Monthly payment recording and local receipts
- Payment history and local reversal marking
- Expense entry and local status management
- Room database and local finance rules

## Data migration
Import the reviewed CSV register from the app. Verify all 57 names, month amounts, and totals against the paper register. Blank cells should be reviewed rather than automatically treated as zero payments. Export a backup after successful import.

## Limitations
- Local-only means one device is the authoritative copy. No live sharing, cloud backup, remote login, or synchronization.
- Local payment reversal marks a payment as reversed for local totals; it is not a bank/mobile-money refund. Keep evidence and audit records outside the app if required by club policy.
- Before production use, test database migration, import/export, balances, and restore from a CSV backup.


## Committee reports (monthly and annual)
- The Reports tab provides monthly and annual views based on local Room records.
- Collections use the contribution period (payment month/year); expenses marked `paid` or `approved` are grouped by their recorded date.
- Financial report PDFs include the selected period, TZS summaries, batch collections, current-month arrears, generation timestamp, page numbers, and committee preparation/review/approval lines.
- Member statements now paginate through all payment records instead of stopping after 25 rows. Long arrears lists paginate instead of being silently limited to 15 names.
- “Net movement” means collections less included expenses. It is **not** a closing cash balance because the report does not capture a verified opening balance. Reconcile it against cashbook, bank, and mobile-money records before committee approval.
- Expected contribution estimates assume TZS 10,000 per active member per month (12 months for the annual view). Confirm this against the club's approved rules before relying on arrears estimates.


## Build and test the report upgrade
1. Open the repository in Android Studio with JDK 17 and Android SDK 35 installed.
2. Sync the Gradle project.
3. Run **Build → Make Project** and then **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. Run **Run → Edit Configurations** and launch the app on an emulator/device with sample members, payments across multiple months, and both paid and pending expenses.
5. In Reports, switch between Monthly and Annual. Verify monthly totals use contribution month/year, annual totals use contribution year, and only paid/approved expenses are included.
6. Generate both financial PDFs and a member statement. Confirm headers/page numbers appear on every page and that statements with more than 25 payments and arrears lists with more than 15 members retain all rows.
7. Run **Build → Select Build Variant → debug**, then **Run → Run 'app'**. If a Gradle installation is available in a terminal, use `gradle testDebugUnitTest` and `gradle assembleDebug`; this repository currently does not include a Gradle wrapper.
