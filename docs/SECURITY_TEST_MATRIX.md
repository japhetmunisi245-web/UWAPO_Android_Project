# UWAPO security and acceptance test matrix

Run these tests against a dedicated staging Supabase project before production.

| Test | Expected result |
|---|---|
| Unauthenticated user reads members | Denied |
| Authenticated user without active club role reads members | Denied |
| Secretary records payment through `record_payment` | Denied |
| Treasurer records positive payment for active club member | Payment, allocation, ledger and audit records created atomically |
| Same payment request UUID retried | Original receipt returned; no second payment |
| Payment amount zero or negative | Rejected |
| Member ID belongs to another club | Rejected |
| Treasurer reverses confirmed payment with reason | Original retained; reversal ledger entry and audit record created |
| Payment reversed twice | Second reversal rejected |
| Treasurer submits expense | Pending expense created; no ledger outflow yet |
| Treasurer approves own expense | Denied |
| Chairperson approves another user's pending expense | Expense becomes paid and exactly one ledger outflow is posted |
| Direct client insert/update/delete of ledger | Denied by RLS / absence of policies |
| Member import contains duplicate member number | Duplicate ignored locally or flagged during staging import |
| Offline payment is queued, then synced twice | One server payment due to stable request UUID |
| Cloud token expired | Worker attempts refresh; if refresh fails, user signs in again |
| Database backup restored in staging | Members and financial totals reconcile |

Also test app upgrades from Room schema versions 1, 2, and 3 using test databases. Never test destructive migrations against the only copy of real data.
