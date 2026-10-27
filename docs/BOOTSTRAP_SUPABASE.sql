-- UWAPO bootstrap template. Replace every UUID/email/name placeholder before running.
-- First create the committee users in Supabase Dashboard > Authentication > Users.
-- Then apply migrations 001 and 002. Run this in the Supabase SQL editor as project owner.

-- 1) Create the club. Replace the UUID with a newly generated UUID and keep it for app configuration.
insert into public.clubs (id, name, currency, timezone)
values ('REPLACE-CLUB-UUID'::uuid, 'UWAPO', 'TZS', 'Africa/Dar_es_Salaam');

-- 2) Create the receiving account. Replace with another UUID and keep it for app configuration.
insert into public.accounts (id, club_id, name, account_type)
values ('REPLACE-ACCOUNT-UUID'::uuid, 'REPLACE-CLUB-UUID'::uuid, 'Cash', 'cash');

-- 3) Add the default monthly contribution rule.
insert into public.contribution_rules (club_id, amount_tzs, frequency, effective_from)
values ('REPLACE-CLUB-UUID'::uuid, 10000, 'monthly', '2026-01-01');

-- 4) Create periods for 2026. Repeat for future years as needed.
insert into public.contribution_periods (club_id, period_start, period_end, due_date, expected_amount_tzs, status)
select 'REPLACE-CLUB-UUID'::uuid,
       make_date(2026, m, 1),
       (make_date(2026, m, 1) + interval '1 month - 1 day')::date,
       (make_date(2026, m, 1) + interval '1 month - 1 day')::date,
       10000, 'open'
from generate_series(1, 12) as m;

-- 5) Add committee users. Create each user in Supabase Auth first, then replace email/name/role.
-- Run one statement per user. Supported roles: admin, chairperson, treasurer, secretary.
insert into public.profiles (id, full_name, phone)
select id, 'REPLACE FULL NAME', null
from auth.users where email = 'REPLACE-USER-EMAIL'
on conflict (id) do update set full_name = excluded.full_name;

insert into public.club_users (club_id, user_id, role, active)
select 'REPLACE-CLUB-UUID'::uuid, id, 'treasurer', true
from auth.users where email = 'REPLACE-USER-EMAIL'
on conflict (club_id, user_id) do update set role = excluded.role, active = true;

-- Important:
-- - Replace placeholder UUIDs with valid UUID values, not literal placeholder text.
-- - Repeat the profile/club_users statements for each committee member.
-- - Never create roles from the Android client.
-- - Do not put a Supabase service-role key in local.properties or the APK.
