-- UWAPO migration 002: transactional expense submission and posting.
-- Apply only after 202610270001_uwapo_initial_schema.sql.

begin;

drop policy if exists expenses_insert_treasurer on public.expenses;
drop policy if exists expenses_update_approver on public.expenses;

create or replace function public.submit_expense(
    p_expense_id uuid,
    p_club_id uuid,
    p_account_id uuid,
    p_category text,
    p_amount_tzs bigint,
    p_expense_date date,
    p_description text
)
returns uuid
language plpgsql
security definer
set search_path = public, auth
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  if not public.has_club_role(p_club_id, array['admin','treasurer']::text[]) then
    raise exception 'Only an authorized treasurer may submit expenses' using errcode = '42501';
  end if;
  if p_amount_tzs is null or p_amount_tzs <= 0 then raise exception 'Expense amount must be positive'; end if;
  if length(trim(coalesce(p_category,''))) = 0 or length(trim(coalesce(p_description,''))) = 0 then
    raise exception 'Category and description are required';
  end if;
  if not exists (select 1 from public.accounts a where a.id=p_account_id and a.club_id=p_club_id and a.active) then
    raise exception 'Active club account not found';
  end if;

  insert into public.expenses(id, club_id, account_id, category, amount_tzs, expense_date,
      description, status, submitted_by)
  values (p_expense_id, p_club_id, p_account_id, trim(p_category), p_amount_tzs,
      coalesce(p_expense_date, current_date), trim(p_description), 'pending', auth.uid())
  on conflict (id) do nothing;

  select id into v_id from public.expenses where id=p_expense_id and club_id=p_club_id;
  if v_id is null then raise exception 'Expense ID already exists for a different record'; end if;
  insert into public.audit_logs(club_id, actor_id, action, entity_type, entity_id, details)
  values (p_club_id, auth.uid(), 'expense_submitted', 'expense', v_id::text,
      jsonb_build_object('amount_tzs', p_amount_tzs, 'category', trim(p_category)));
  return v_id;
end;
$$;

create or replace function public.approve_and_post_expense(p_club_id uuid, p_expense_id uuid)
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare v_expense public.expenses%rowtype;
begin
  if auth.uid() is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  if not public.has_club_role(p_club_id, array['admin','chairperson']::text[]) then
    raise exception 'Only chairperson/admin may approve expenses' using errcode = '42501';
  end if;
  select * into v_expense from public.expenses
    where id=p_expense_id and club_id=p_club_id for update;
  if not found then raise exception 'Expense not found'; end if;
  if v_expense.status <> 'pending' then raise exception 'Only pending expenses can be approved'; end if;
  if v_expense.submitted_by = auth.uid() then raise exception 'Submitter cannot approve their own expense'; end if;

  update public.expenses set status='paid', approved_by=auth.uid(), approved_at=now()
    where id=p_expense_id;
  insert into public.ledger_entries(club_id, account_id, entry_type, direction, amount_tzs,
      expense_id, reference, description, created_by)
  values (p_club_id, v_expense.account_id, 'expense', 'out', v_expense.amount_tzs,
      v_expense.id, 'EXP-' || upper(substr(replace(v_expense.id::text,'-',''),1,10)),
      v_expense.category || ': ' || v_expense.description, auth.uid());
  insert into public.audit_logs(club_id, actor_id, action, entity_type, entity_id, details)
  values (p_club_id, auth.uid(), 'expense_approved_and_posted', 'expense', v_expense.id::text,
      jsonb_build_object('amount_tzs', v_expense.amount_tzs, 'submitted_by', v_expense.submitted_by));
end;
$$;

revoke all on function public.submit_expense(uuid,uuid,uuid,text,bigint,date,text) from public;
revoke all on function public.approve_and_post_expense(uuid,uuid) from public;
grant execute on function public.submit_expense(uuid,uuid,uuid,text,bigint,date,text) to authenticated;
grant execute on function public.approve_and_post_expense(uuid,uuid) to authenticated;

commit;
