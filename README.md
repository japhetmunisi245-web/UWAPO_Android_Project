# UWAPO Android — Committee-Ready Reporting Edition

Native Android membership and contribution management application for **UWAPO**. Designed for local-only, secure, offline operation on Android devices with complete Room/SQLite storage and professional committee reporting.

## 1. Local-Only Architecture
- **Target Platform**: Android (Kotlin, Jetpack Compose, Material 3).
- **Storage**: Room / SQLite local database with SQLCipher encrypted passphrase support.
- **Security & Privacy**: No cloud dependencies, no remote servers, no external API calls, and no remote synchronization workers.
- **Local Features**:
  - Member management and profile history.
  - Payment entry, receipt generation, and payment reversal.
  - Expense tracking with local approval workflow (`pending_approval`, `approved`, `paid`, `rejected`).
  - Automated local database backups via `ScheduledBackupWorker`.
  - CSV import/export for batch records.

## 2. Monthly and Annual Committee Reporting
The app provides a reporting hub supporting two distinct reporting periods:

### Monthly Reports
- **Selected Month Collections**: Total recorded payments for the selected contribution month and year (`periodMonth` & `periodYear`), excluding reversed transactions.
- **Approved Monthly Expenses**: Total expenses with status `paid` or `approved` recorded during that specific month/year based on `expenseDate`.
- **Monthly Net Movement**: `Collections - Included Expenses`.
- **Expected Monthly Contributions**: `Active Member Count × TZS 10,000`.
- **Estimated Outstanding Arrears**: Estimated shortfall (`Expected - Collections`).
- **Members in Arrears List**: Active members who have pending contributions for the selected month, with quick SMS reminder options.

### Annual Reports
- **Annual Collections**: Sum of non-reversed payments recorded for the selected year (`periodYear`).
- **Annual Approved Expenses**: Sum of expenses approved/paid during that year (`expenseDate`).
- **Annual Net Movement**: `Annual Collections - Annual Expenses`.
- **Expected Annual Contributions**: `Active Member Count × TZS 120,000`.
- **Monthly Collection Breakdown**: Month-by-month contribution summary table (January to December).
- **Annual Arrears Summary**: Active members with annual totals below the expected requirement.

## 3. Professional Multi-Page PDF Reports
PDF generation in `PdfGenerator.kt` provides committee-ready PDF documents:
- **UWAPO Navy & White Design**: Header banner with branding, subtitle, and generation timestamp.
- **Multi-Page Pagination Engine**: Automatically splits long content across multiple A4 pages without truncating data.
- **Repeated Table Headers**: Tables spanning across pages automatically repeat header titles on subsequent pages.
- **Footer Page Numbering**: Each page includes "UWAPO Management System · Page X of Y".
- **Committee Sign-Off Block**: Dedicated preparation, review, and approval signature lines on the final page of financial reports:
  ```
  Prepared by: ________________________   Date: ______________
  Reviewed by: ________________________   Date: ______________
  Approved by: ________________________   Date: ______________
  ```

## 4. Member Statements
Individual member statements generated from profile dialogs feature:
- Complete payment history without row limits or truncation.
- Columns: Date (`dd/MM/yyyy`), Contribution Period (`MM/YYYY`), Method, Receipt No., Status, and Amount (TZS).
- Clear identification and exclusion of `REVERSED` payments from totals.
- Multi-page support with repeated headers and footer page numbering.

## 5. Financial Safeguards & Principles
- **Net Movement**: Calculated strictly as `Collections - Approved Expenses`.
- **Expense Exclusion**: Pending and rejected expenses are excluded from financial totals.
- **Reversal Exclusion**: Reversed payments are excluded from collection totals.
- **Period Integrity**: Payments are filtered by contribution period (`periodYear`/`periodMonth`), whereas expenses are filtered by recorded expense date (`expenseDate`).

## 6. Build Instructions
1. Install Android Studio (Ladybug or later), Android SDK 35, and JDK 17.
2. Open the project root folder in Android Studio.
3. Allow Gradle sync to complete.
4. Execute unit tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
5. Assemble debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
6. The debug APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

## 7. Manual Verification Procedures
1. **Reporting Switch**: Open Reports tab and toggle between "Monthly Report" and "Annual Report". Verify totals recalculate accurately.
2. **PDF Generation**: Generate PDF reports for both Monthly and Annual views. Verify pagination, repeated headers, page numbers, and committee sign-off block.
3. **Member Statement PDF**: Open a member's profile and export PDF statement. Verify all historical payment records are displayed without truncation.
4. **CSV Import/Export**: Import a member register CSV and export backup CSV from the Reports tab.
5. **Database Persistence**: Restart the app and confirm local Room records survive app restarts.
