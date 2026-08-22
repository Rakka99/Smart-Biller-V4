-- BMAX V2 core schema. Review before applying to an existing production project.
create extension if not exists pgcrypto;

do $$ begin
  create type public.user_role as enum ('ADMIN','SUPERVISOR','BILLER');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.billing_status as enum ('UNPAID','PENDING','PAID','FAILED');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.billing_category as enum ('PREVENTIF','KOREKTIF','IRISAN');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.payment_status as enum ('PENDING','SUCCESS','FAILED','UNKNOWN');
exception when duplicate_object then null; end $$;

create table if not exists public.regions (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.ulps (
  id uuid primary key default gen_random_uuid(),
  region_id uuid not null references public.regions(id),
  code text not null unique,
  name text not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null default '',
  role public.user_role not null default 'BILLER',
  region_id uuid references public.regions(id),
  ulp_id uuid references public.ulps(id),
  biller_id uuid,
  phone text,
  avatar_url text,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.billers (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null unique references public.profiles(id),
  ulp_id uuid not null references public.ulps(id),
  code text not null unique,
  name text not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.rbms (
  id uuid primary key default gen_random_uuid(),
  biller_id uuid not null references public.billers(id),
  ulp_id uuid not null references public.ulps(id),
  code text not null check (code in ('A','B','C','D','E')),
  name text not null,
  sequence integer not null default 1,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (biller_id, code)
);

create table if not exists public.customers (
  id uuid primary key default gen_random_uuid(),
  idpel text not null unique,
  meter_number text,
  name text not null,
  phone text,
  address text,
  village text,
  district text,
  city text,
  postal_code text,
  tariff text,
  power_va integer,
  region_id uuid not null references public.regions(id),
  ulp_id uuid not null references public.ulps(id),
  biller_id uuid not null references public.billers(id),
  rbm_id uuid not null references public.rbms(id),
  rbm_code text not null check (rbm_code in ('A','B','C','D','E')),
  langkah integer not null default 0,
  gardu text,
  tiang text,
  latitude double precision,
  longitude double precision,
  status text,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.billings (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references public.customers(id),
  period text not null,
  amount numeric not null default 0,
  admin_fee numeric not null default 0,
  penalty numeric not null default 0,
  total numeric not null default 0,
  due_date date,
  status public.billing_status not null default 'UNPAID',
  category public.billing_category not null,
  paid_at timestamptz,
  source text,
  source_ref_id text,
  raw_data jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (customer_id, period)
);

create table if not exists public.inquiries (
  id uuid primary key default gen_random_uuid(),
  ref_id text not null unique,
  customer_id uuid not null references public.customers(id),
  provider text not null default 'IAK',
  iak_tr_id bigint,
  period text,
  amount numeric,
  admin_fee numeric,
  penalty numeric,
  total numeric,
  status text,
  response_code text,
  message text,
  raw_response jsonb,
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.payments (
  id uuid primary key default gen_random_uuid(),
  ref_id text not null unique,
  inquiry_id uuid references public.inquiries(id),
  customer_id uuid not null references public.customers(id),
  biller_id uuid not null references public.billers(id),
  rbm_id uuid not null references public.rbms(id),
  created_by uuid references auth.users(id),
  iak_tr_id bigint unique,
  period text,
  amount numeric not null default 0,
  admin_fee numeric not null default 0,
  penalty numeric not null default 0,
  total numeric not null default 0,
  status public.payment_status not null default 'PENDING',
  serial_number text,
  response_code text,
  message text,
  raw_response jsonb,
  paid_at timestamptz,
  last_status_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.invoices (
  id uuid primary key default gen_random_uuid(),
  invoice_no text not null unique,
  payment_id uuid not null unique references public.payments(id),
  customer_id uuid not null references public.customers(id),
  total numeric not null default 0,
  status text not null default 'ISSUED',
  issued_at timestamptz not null default now(),
  printed_at timestamptz,
  metadata jsonb
);

create table if not exists public.pdil_records (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references public.customers(id),
  field_name text not null,
  old_value text,
  new_value text,
  requested_by uuid references auth.users(id),
  reviewed_by uuid references auth.users(id),
  approved_by uuid references auth.users(id),
  status text not null default 'DRAFT',
  notes text,
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  approved_at timestamptz
);

create table if not exists public.audit_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id),
  action text not null,
  entity text not null,
  entity_id uuid,
  description text,
  old_data jsonb,
  new_data jsonb,
  ip_address inet,
  device_id text,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.billers enable row level security;
alter table public.rbms enable row level security;
alter table public.customers enable row level security;
alter table public.billings enable row level security;
alter table public.inquiries enable row level security;
alter table public.payments enable row level security;
alter table public.invoices enable row level security;
alter table public.pdil_records enable row level security;
alter table public.audit_logs enable row level security;

create index if not exists idx_customers_idpel on public.customers(idpel);
create index if not exists idx_customers_biller on public.customers(biller_id);
create index if not exists idx_customers_rbm on public.customers(rbm_id);
create index if not exists idx_customers_ulp on public.customers(ulp_id);
create index if not exists idx_billings_customer_period on public.billings(customer_id, period);
create index if not exists idx_billings_status_category on public.billings(status, category);
create index if not exists idx_payments_ref on public.payments(ref_id);
create index if not exists idx_payments_trid on public.payments(iak_tr_id);
create index if not exists idx_payments_scope on public.payments(biller_id, rbm_id);
create index if not exists idx_rbms_scope on public.rbms(biller_id, ulp_id);

-- SECURITY DEFINER helpers. Execute permissions stay restricted; expose through RLS only.
create or replace function public.get_current_role()
returns public.user_role
language sql
stable
security definer
set search_path = public
as $$ select role from public.profiles where id = auth.uid() $$;

create or replace function public.get_current_biller_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$ select biller_id from public.profiles where id = auth.uid() $$;

create or replace function public.get_current_ulp_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$ select ulp_id from public.profiles where id = auth.uid() $$;

create or replace function public.get_current_region_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$ select region_id from public.profiles where id = auth.uid() $$;

revoke all on function public.get_current_role() from public, anon, authenticated;
revoke all on function public.get_current_biller_id() from public, anon, authenticated;
revoke all on function public.get_current_ulp_id() from public, anon, authenticated;
revoke all on function public.get_current_region_id() from public, anon, authenticated;

-- Core customer scope policy. Payment/inquiry writes should be performed by Edge Functions.
drop policy if exists customers_select_scope on public.customers;
create policy customers_select_scope on public.customers
for select to authenticated
using (
  public.get_current_role() = 'ADMIN'
  or (public.get_current_role() = 'SUPERVISOR' and ulp_id = public.get_current_ulp_id())
  or (public.get_current_role() = 'BILLER' and biller_id = public.get_current_biller_id())
);

drop policy if exists billings_select_scope on public.billings;
create policy billings_select_scope on public.billings
for select to authenticated
using (
  public.get_current_role() = 'ADMIN'
  or exists (
    select 1 from public.customers c
    where c.id = billings.customer_id
      and (c.biller_id = public.get_current_biller_id() or c.ulp_id = public.get_current_ulp_id())
  )
);

drop policy if exists payments_select_scope on public.payments;
create policy payments_select_scope on public.payments
for select to authenticated
using (
  public.get_current_role() = 'ADMIN'
  or (public.get_current_role() = 'SUPERVISOR' and exists (select 1 from public.billers b where b.id = payments.biller_id and b.ulp_id = public.get_current_ulp_id()))
  or (public.get_current_role() = 'BILLER' and payments.biller_id = public.get_current_biller_id())
);

-- No direct Android status mutation. Provider callbacks / Edge Functions should own payment transitions.
-- IAK credentials must remain server-side secrets.
