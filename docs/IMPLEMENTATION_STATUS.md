# Implementation status

## Implemented in this package
- Android project configuration for Kotlin, Compose, Material 3, and Room.
- Dashboard UI with local counts and recorded totals.
- Add-member dialog and local persistence.
- Record-payment dialog with member, amount, month, year, and payment method.
- Payment history screen and receipt reference generation for local demo records.
- Local reports screen.
- Initial Supabase SQL migration and import CSV template.

## Not implemented / not verified
- Cloud authentication and Supabase client connection.
- Backend user provisioning and production-ready committee workflows.
- Cloud synchronization, WorkManager queue, and conflict resolution.
- Cloud-issued receipts and server idempotency wired into the app.
- Expense approval/posting UI and server-side posting RPC.
- PDF/CSV report export.
- Historical register transcription and reconciliation.
- Automated app/database integration tests.
- Compiled APK. This environment has Java but no Gradle executable or Android SDK, so a real Android build could not be run here.
