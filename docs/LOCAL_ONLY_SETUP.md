# UWAPO Local-Only Setup

1. Open the project in Android Studio with JDK 17 and Android SDK 35.
2. Build and install on the Android device used by the treasurer.
3. Import the reviewed member/contribution CSV.
4. Verify the member count and totals against the original paper register.
5. Export a CSV backup after import and on a regular schedule.

## Privacy and data-loss warnings
This build has no cloud service and no internet permission. Data lives only in the app's local Room database. Other committee devices cannot see it. Clearing app data or uninstalling may delete records; export backups and protect them.

## Before relying on it
Build the APK, test adding members and payments, verify reversed payments are excluded from totals, test CSV export/import, and retain source documents. Do not treat a locally recorded payment as proof of an external bank/mobile-money transfer.
