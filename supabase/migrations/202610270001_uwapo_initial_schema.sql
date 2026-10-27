-- UWAPO Membership Club: initial Supabase/PostgreSQL migration
-- Apply to a new Supabase project after reviewing in staging.
-- All monetary values are integer TZS. Never use floating-point for money.

begin;

create extension if not exists pgcrypto;

create table if not exists public.clubs (
    id uuid primary key default gen_random_uuid(),
    name text not null default 'UWAPO',
    currency char(3) not null default 'TZS' check (currency = 'TZS'),
    timezone text not null default 'Africa/Dar_es_Salaam',
    created_at timestamptz not null default now()
);

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    full_name text not null,
    phone text,
    active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.club_users (
    club_id uuid not null references public.clubs(id) on delete restrict,
    user_id uuid not null references public.profiles(id) on delete cascade,
    role text not null check (role in ('admin','chairperson','treasurer','secretary')),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    primary key (club_id, user_id)
);

create table if not exists public.members (
    id uuid primary key default gen_random_uuid(),
    club_id uuid not null references public.clubs(id) on delete restrict,
    member_no text not null,
    full_name text not null,
    phone text,
    join_date date,
    status text not null default 'active' check (status in ('active','inactive','deceased','left')),
    notes text,
    imported_source text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (club_id, member_no)
);

create table if not exists public.contribution_rules (
    id uuid primary key default gen_random_uuid(),
    club_id uuid not null references public.clubs(id) on delete restrict,
    amount_tzs bigint not null check (amount_tzs > 0),
    frequency text not null default 'monthly' check (frequency = 'monthly'),
    effective_from date not null,
    effective_to date,
    created_by uuid references auth.users(id),
    created_at timestamptz not null default now(),
    check (effective_to is null or effective_to >= effective_from)
);

create table if not exists public.contribution_periods (
    id uuid primary key default gen_random_uuid(),
    club_id uuid not null references public.clubs(id) on delete restrict,
    period_start date not null,
    period_end date not null,
    due_date date not null,
    expected_amount_tzs bigint not null default 10000 check (expected_amount_tzs >= 0),
    status text not null default 'open' check (status in ('open','closed')),
    created_at timestamptz not null default now(),
    check (period_end >= period_start),
    unique (club_id, period_start),
    unique (club_id, period_end)
);

create table if not exists public.accounts (
    id uuid primary key default gen_random_uuid(),
    club_id uuid not null references public.clubs(id) on delete restrict,
    name text not null,
    account_type text not null check (account_type in ('cash','bank','mobile_money','other')),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    unique (club_id, name)
);

create table if not exists public.payments (
    id uuid primary key default gen_random_uuid(),
    club_id uuid not null references public.clubs(id) on delete restrict,
    member_id uuid not null references public.members(id) on delete restrict,
    period_id uuid references public.contribution_periods(id) on delete restrict,
    amount_tzs bigint not null check (amount_tzs > 0),
    paid_at timestamptz not null default now(),
    method text not null check (method in ('cash','bank','mobile_money','other')),
    account_id uuid not null references public.accounts(id) on delete restrict,
    receipt_no text not null,
    request_id uuid not null,
    status text not null default 'confirmed' check (status in ('confirmed','reversed')),
    notes text,
    received_by uuid not null references auth.users(id),
    reversed_at timestamptz,
    reversed_by uuid references auth.users(id),
    reversal_reason text,
    created_at timestamptz not null default now(),
    unique (club_id, receipt_no),
    unique (club_id, request_id),
    check (
      (status = 'confirmed' and reversed_at is null and reversed_by is null and reversal_reason is null)
      or
      (status = 'reversed' and reversed_at is not null and reversed_by is not null and length(trim(coalesce(reversal_reason,''))) > 0)
    )
);

create table if not exists public.payment_allocations (
    id uuid primary key default gen_random_uuid(),
    club_id uuid not null references public.clubs(id) on delete restrict,
    payment_id uuid not null references public.payments(id) on delete restrict,
    period_id uuid not null references public.contribution_periods(id) on delete restrict,
    amount_tzs bigint not null check (amount_tzs > 0),
    created_at timestamptz not null default now()
);

create table if not exists public.expenses (
    id uuid primary key default gen_random_uuid(),
    club_id uuid not null references public.clubs(id) on delete restrict,
    account_id uuid not null references public.accounts(id) on delete restrict,
    category text not null,
    amount_tzs bigint not null check (amount_tzs > 0),
    expense_date date not null default current_date,
    description text not null,
    receipt_reference text,
    status text not null default 'pending' check (status in ('pending','approved','rejected','paid','reversed')),
    submitted_by uuid not null references auth.users(id),
    approved_by uuid references auth.users(id),
    approved_at timestamptz,
    created_at timestamptz not null default now()
);

-- Immutable-by-policy ledger: records confirmed inflows/outflows and reversals.
create table if not exists public.ledger_entries (
    id uuid primary key default gen_random_uuid(),
    club_id uuid not null references public.clubs(id) on delete restrict,
    account_id uuid not null references public.accounts(id) on delete restrict,
    entry_type text not null check (entry_type in ('payment','expense','payment_reversal','opening_balance','adjustment','transfer_in','transfer_out')),
    direction text not null check (direction in ('in','out')),
    amount_tzs bigint not null check (amount_tzs > 0),
    payment_id uuid references public.payments(id) on delete restrict,
    expense_id uuid references public.expenses(id) on delete restrict,
    reference text not null,
    description text,
    created_by uuid references auth.users(id),
    created_at timestamptz not null default now()
);

create table if not exists public.audit_logs (
    id bigint generated always as identity primary key,
    club_id uuid not null references public.clubs(id) on delete restrict,
    actor_id uuid references auth.users(id),
    action text not null,
    entity_type text not null,
    entity_id text,
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create table if not exists public.meetings (
    id uuid primary key default gen_random_uuid(),
    club_id uuid not null references public.clubs(id) on delete restrict,
    meeting_date timestamptz not null,
    title text not null,
    agenda text,
    minutes text,
    created_by uuid references auth.users(id),
    created_at timestamptz not null default now()
);

create table if not exists public.attendance (
    id uuid primary key default gen_random_uuid(),
    club_id uuid not null references public.clubs(id) on delete restrict,
    meeting_id uuid not null references public.meetings(id) on delete restrict,
    member_id uuid not null references public.members(id) on delete restrict,
    status text not null check (status in ('present','absent','excused')),
    created_at timestamptz not null default now(),
    unique (meeting_id, member_id)
);

create index if not exists idx_members_club_status on public.members(club_id, status);
create index if not exists idx_payments_club_paid_at on public.payments(club_id, paid_at desc);
create index if not exists idx_payments_member on public.payments(member_id, paid_at desc);
create index if not exists idx_allocations_period on public.payment_allocations(period_id);
create index if not exists idx_ledger_account_date on public.ledger_entries(account_id, created_at desc);
create index if not exists idx_audit_club_created on public.audit_logs(club_id, created_at desc);

-- Security-definer helpers avoid recursive RLS checks against club_users.
create or replace function public.is_club_user(p_club_id uuid)
returns boolean
language sql stable security definer
set search_path = public, auth
as $$
  select exists (
    select 1 from public.club_users cu
    join public.profiles p on p.id = cu.user_id
    where cu.club_id = p_club_id
      and cu.user_id = auth.uid()
      and cu.active = true
      and p.active = true
  );
$$;

create or replace function public.has_club_role(p_club_id uuid, p_roles text[])
returns boolean
language sql stable security definer
set search_path = public, auth
as $$
  select exists (
    select 1 from public.club_users cu
    join public.profiles p on p.id = cu.user_id
    where cu.club_id = p_club_id
      and cu.user_id = auth.uid()
      and cu.active = true
      and p.active = true
      and cu.role = any(p_roles)
  );
$$;

revoke all on function public.is_club_user(uuid) from public;
revoke all on function public.has_club_role(uuid,text[]) from public;
grant execute on function public.is_club_user(uuid) to authenticated;
grant execute on function public.has_club_role(uuid,text[]) to authenticated;

-- Updated timestamp helper.
create or replace function public.set_updated_at()
returns trigger language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists members_set_updated_at on public.members;
create trigger members_set_updated_at before update on public.members
for each row execute function public.set_updated_at();

-- Record a payment and its ledger entry in one database transaction.
-- This MVP allocates a payment to one selected period. For multi-period/advance
-- payments, add a separate allocation RPC that validates the total allocation.
create or replace function public.record_payment(
    p_club_id uuid,
    p_member_id uuid,
    p_period_id uuid,
    p_amount_tzs bigint,
    p_paid_at timestamptz,
    p_method text,
    p_account_id uuid,
    p_request_id uuid,
    p_notes text default null
)
returns table(payment_id uuid, receipt_no text, saved_amount_tzs bigint)
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_payment_id uuid;
  v_receipt text;
  v_existing public.payments%rowtype;
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;

  if not public.has_club_role(p_club_id, array['admin','treasurer']::text[]) then
    raise exception 'Insufficient role to record payments' using errcode = '42501';
  end if;

  if p_amount_tzs is null or p_amount_tzs <= 0 then
    raise exception 'Payment amount must be greater than zero';
  end if;

  if p_method not in ('cash','bank','mobile_money','other') then
    raise exception 'Unsupported payment method';
  end if;

  -- Idempotency: a retry with the same request UUID returns the original result.
  select * into v_existing
  from public.payments p
  where p.club_id = p_club_id and p.request_id = p_request_id;

  if found then
    return query select v_existing.id, v_existing.receipt_no, v_existing.amount_tzs;
    return;
  end if;

  if not exists (
    select 1 from public.members m
    where m.id = p_member_id and m.club_id = p_club_id and m.status = 'active'
  ) then
    raise exception 'Active member not found in this club';
  end if;

  if p_period_id is not null and not exists (
    select 1 from public.contribution_periods cp
    where cp.id = p_period_id and cp.club_id = p_club_id
  ) then
    raise exception 'Contribution period not found in this club';
  end if;

  if not exists (
    select 1 from public.accounts a
    where a.id = p_account_id and a.club_id = p_club_id and a.active = true
  ) then
    raise exception 'Active receiving account not found in this club';
  end if;

  v_payment_id := gen_random_uuid();
  v_receipt := 'UWP-' || to_char(coalesce(p_paid_at, now()) at time zone 'Africa/Dar_es_Salaam', 'YYYYMMDD') || '-' ||
               upper(substr(replace(v_payment_id::text, '-', ''), 1, 8));

  insert into public.payments (
    id, club_id, member_id, period_id, amount_tzs, paid_at, method,
    account_id, receipt_no, request_id, notes, received_by
  ) values (
    v_payment_id, p_club_id, p_member_id, p_period_id, p_amount_tzs,
    coalesce(p_paid_at, now()), p_method, p_account_id, v_receipt,
    p_request_id, p_notes, auth.uid()
  );

  if p_period_id is not null then
    insert into public.payment_allocations(club_id, payment_id, period_id, amount_tzs)
    values (p_club_id, v_payment_id, p_period_id, p_amount_tzs);
  end if;

  insert into public.ledger_entries(
    club_id, account_id, entry_type, direction, amount_tzs,
    payment_id, reference, description, created_by
  ) values (
    p_club_id, p_account_id, 'payment', 'in', p_amount_tzs,
    v_payment_id, v_receipt, 'Member contribution', auth.uid()
  );

  insert into public.audit_logs(club_id, actor_id, action, entity_type, entity_id, details)
  values (
    p_club_id, auth.uid(), 'payment_recorded', 'payment', v_payment_id::text,
    jsonb_build_object('receipt_no', v_receipt, 'amount_tzs', p_amount_tzs, 'member_id', p_member_id, 'period_id', p_period_id)
  );

  return query select v_payment_id, v_receipt, p_amount_tzs;
end;
$$;

revoke all on function public.record_payment(uuid,uuid,uuid,bigint,timestamptz,text,uuid,uuid,text) from public;
grant execute on function public.record_payment(uuid,uuid,uuid,bigint,timestamptz,text,uuid,uuid,text) to authenticated;

-- Reversal preserves the original payment and appends an offsetting ledger entry.
create or replace function public.reverse_payment(
    p_club_id uuid,
    p_payment_id uuid,
    p_reason text
)
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_payment public.payments%rowtype;
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if not public.has_club_role(p_club_id, array['admin','treasurer','chairperson']::text[]) then
    raise exception 'Insufficient role to reverse payments' using errcode = '42501';
  end if;
  if length(trim(coalesce(p_reason,''))) < 5 then
    raise exception 'A meaningful reversal reason is required';
  end if;

  select * into v_payment
  from public.payments p
  where p.id = p_payment_id and p.club_id = p_club_id
  for update;

  if not found then raise exception 'Payment not found'; end if;
  if v_payment.status = 'reversed' then raise exception 'Payment already reversed'; end if;

  update public.payments
  set status = 'reversed', reversed_at = now(), reversed_by = auth.uid(), reversal_reason = trim(p_reason)
  where id = p_payment_id;

  insert into public.ledger_entries(
    club_id, account_id, entry_type, direction, amount_tzs,
    payment_id, reference, description, created_by
  ) values (
    p_club_id, v_payment.account_id, 'payment_reversal', 'out',
    v_payment.amount_tzs, v_payment.id, v_payment.receipt_no || '-REV',
    'Reversal: ' || trim(p_reason), auth.uid()
  );

  insert into public.audit_logs(club_id, actor_id, action, entity_type, entity_id, details)
  values (
    p_club_id, auth.uid(), 'payment_reversed', 'payment', p_payment_id::text,
    jsonb_build_object('receipt_no', v_payment.receipt_no, 'amount_tzs', v_payment.amount_tzs, 'reason', trim(p_reason))
  );
end;
$$;

revoke all on function public.reverse_payment(uuid,uuid,text) from public;
grant execute on function public.reverse_payment(uuid,uuid,text) to authenticated;

-- Enable RLS everywhere that contains club or user data.
alter table public.clubs enable row level security;
alter table public.profiles enable row level security;
alter table public.club_users enable row level security;
alter table public.members enable row level security;
alter table public.contribution_rules enable row level security;
alter table public.contribution_periods enable row level security;
alter table public.accounts enable row level security;
alter table public.payments enable row level security;
alter table public.payment_allocations enable row level security;
alter table public.expenses enable row level security;
alter table public.ledger_entries enable row level security;
alter table public.audit_logs enable row level security;
alter table public.meetings enable row level security;
alter table public.attendance enable row level security;

-- Profiles: users may read/update their own basic profile.
drop policy if exists profiles_select_self on public.profiles;
create policy profiles_select_self on public.profiles for select to authenticated
using (id = auth.uid());
drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles for update to authenticated
using (id = auth.uid()) with check (id = auth.uid());

-- Membership-based read access.
drop policy if exists clubs_select_member on public.clubs;
create policy clubs_select_member on public.clubs for select to authenticated
using (public.is_club_user(id));

drop policy if exists club_users_select_member on public.club_users;
create policy club_users_select_member on public.club_users for select to authenticated
using (public.is_club_user(club_id));

drop policy if exists members_select_committee on public.members;
create policy members_select_committee on public.members for select to authenticated
using (public.is_club_user(club_id));

drop policy if exists members_insert_secretary_admin on public.members;
create policy members_insert_secretary_admin on public.members for insert to authenticated
with check (public.has_club_role(club_id, array['admin','secretary']::text[]));

drop policy if exists members_update_secretary_admin on public.members;
create policy members_update_secretary_admin on public.members for update to authenticated
using (public.has_club_role(club_id, array['admin','secretary']::text[]))
with check (public.has_club_role(club_id, array['admin','secretary']::text[]));

drop policy if exists contribution_rules_select on public.contribution_rules;
create policy contribution_rules_select on public.contribution_rules for select to authenticated
using (public.is_club_user(club_id));
drop policy if exists contribution_rules_manage on public.contribution_rules;
drop policy if exists contribution_rules_insert on public.contribution_rules;
drop policy if exists contribution_rules_update on public.contribution_rules;
create policy contribution_rules_insert on public.contribution_rules for insert to authenticated
with check (public.has_club_role(club_id, array['admin','chairperson']::text[]));
create policy contribution_rules_update on public.contribution_rules for update to authenticated
using (public.has_club_role(club_id, array['admin','chairperson']::text[]))
with check (public.has_club_role(club_id, array['admin','chairperson']::text[]));

drop policy if exists periods_select on public.contribution_periods;
create policy periods_select on public.contribution_periods for select to authenticated
using (public.is_club_user(club_id));
drop policy if exists periods_manage on public.contribution_periods;
drop policy if exists periods_insert on public.contribution_periods;
drop policy if exists periods_update on public.contribution_periods;
create policy periods_insert on public.contribution_periods for insert to authenticated
with check (public.has_club_role(club_id, array['admin','treasurer']::text[]));
create policy periods_update on public.contribution_periods for update to authenticated
using (public.has_club_role(club_id, array['admin','treasurer']::text[]))
with check (public.has_club_role(club_id, array['admin','treasurer']::text[]));

drop policy if exists accounts_select on public.accounts;
create policy accounts_select on public.accounts for select to authenticated
using (public.is_club_user(club_id));
drop policy if exists accounts_manage on public.accounts;
drop policy if exists accounts_insert on public.accounts;
drop policy if exists accounts_update on public.accounts;
create policy accounts_insert on public.accounts for insert to authenticated
with check (public.has_club_role(club_id, array['admin','chairperson']::text[]));
create policy accounts_update on public.accounts for update to authenticated
using (public.has_club_role(club_id, array['admin','chairperson']::text[]))
with check (public.has_club_role(club_id, array['admin','chairperson']::text[]));

drop policy if exists payments_select on public.payments;
create policy payments_select on public.payments for select to authenticated
using (public.is_club_user(club_id));
-- Intentionally no direct INSERT/UPDATE/DELETE policy on payments.
-- Use record_payment() and reverse_payment() RPCs only.

drop policy if exists allocations_select on public.payment_allocations;
create policy allocations_select on public.payment_allocations for select to authenticated
using (public.is_club_user(club_id));
-- Intentionally no direct write policies for payment allocations.

drop policy if exists expenses_select on public.expenses;
create policy expenses_select on public.expenses for select to authenticated
using (public.is_club_user(club_id));
drop policy if exists expenses_insert_treasurer on public.expenses;
create policy expenses_insert_treasurer on public.expenses for insert to authenticated
with check (public.has_club_role(club_id, array['admin','treasurer']::text[]) and submitted_by = auth.uid());
drop policy if exists expenses_update_approver on public.expenses;
create policy expenses_update_approver on public.expenses for update to authenticated
using (public.has_club_role(club_id, array['admin','chairperson']::text[]))
with check (public.has_club_role(club_id, array['admin','chairperson']::text[]));
-- For production, approved/paid expense ledger posting should be implemented in a
-- dedicated transactional RPC before enabling expense payments.

drop policy if exists ledger_select on public.ledger_entries;
create policy ledger_select on public.ledger_entries for select to authenticated
using (public.is_club_user(club_id));
-- Intentionally no direct write/update/delete policies on ledger_entries.

drop policy if exists audit_select_admin on public.audit_logs;
create policy audit_select_admin on public.audit_logs for select to authenticated
using (public.has_club_role(club_id, array['admin','chairperson']::text[]));
-- Intentionally no direct client write/update/delete policies on audit_logs.

drop policy if exists meetings_select on public.meetings;
create policy meetings_select on public.meetings for select to authenticated
using (public.is_club_user(club_id));
drop policy if exists meetings_manage on public.meetings;
drop policy if exists meetings_insert on public.meetings;
drop policy if exists meetings_update on public.meetings;
create policy meetings_insert on public.meetings for insert to authenticated
with check (public.has_club_role(club_id, array['admin','secretary','chairperson']::text[]));
create policy meetings_update on public.meetings for update to authenticated
using (public.has_club_role(club_id, array['admin','secretary','chairperson']::text[]))
with check (public.has_club_role(club_id, array['admin','secretary','chairperson']::text[]));

drop policy if exists attendance_select on public.attendance;
create policy attendance_select on public.attendance for select to authenticated
using (public.is_club_user(club_id));
drop policy if exists attendance_manage on public.attendance;
drop policy if exists attendance_insert on public.attendance;
drop policy if exists attendance_update on public.attendance;
create policy attendance_insert on public.attendance for insert to authenticated
with check (public.has_club_role(club_id, array['admin','secretary']::text[]));
create policy attendance_update on public.attendance for update to authenticated
using (public.has_club_role(club_id, array['admin','secretary']::text[]))
with check (public.has_club_role(club_id, array['admin','secretary']::text[]));

-- No public signup policy grants club membership. Provision users and roles through
-- a trusted admin workflow (dashboard/edge function), never from an untrusted client.

commit;
