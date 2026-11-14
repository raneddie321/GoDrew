-- Bikur Rofe — database schema.
-- Apply in Supabase → SQL Editor (or `supabase db push`). Times are Israel time (Asia/Jerusalem).

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- Profiles & roles
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
  id uuid primary key references auth.users on delete cascade,
  full_name text,
  phone text,
  role text not null default 'patient' check (role in ('patient', 'staff', 'admin')),
  created_at timestamptz not null default now()
);
alter table public.profiles enable row level security;

create or replace function public.is_staff()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles where id = auth.uid() and role in ('staff', 'admin'));
$$;
grant execute on function public.is_staff() to anon, authenticated;

create policy "read own profile or staff" on public.profiles for select using (auth.uid() = id or public.is_staff());
create policy "update own profile" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);
create policy "admins update profiles" on public.profiles for update using (public.is_staff());
-- Patients may edit their name and phone, never their role.
revoke update on public.profiles from authenticated;
grant update (full_name, phone) on public.profiles to authenticated;

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, full_name, phone)
  values (new.id, new.raw_user_meta_data->>'full_name', new.raw_user_meta_data->>'phone')
  on conflict (id) do nothing;
  return new;
end;
$$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- Catalog: services, branches, doctors, schedules
-- ---------------------------------------------------------------------------
create table if not exists public.services (
  slug text primary key,
  name text not null,
  short text not null default '',
  description text not null default '',
  icon text not null default 'stethoscope',
  walk_in boolean not null default false,
  sort integer not null default 100
);

create table if not exists public.branches (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null,
  name text not null,
  city text not null,
  address text not null default '',
  phone text,
  lat double precision,
  lng double precision,
  -- [{ "days": "א׳–ה׳", "open": "08:00", "close": "23:00" }, ...]
  hours jsonb not null default '[]'::jsonb,
  urgent_care boolean not null default false,
  parking text,
  accessible boolean not null default true,
  active boolean not null default true,
  is_sample boolean not null default false,
  sort integer not null default 100
);

create table if not exists public.branch_services (
  branch_id uuid not null references public.branches on delete cascade,
  service_slug text not null references public.services on delete cascade,
  primary key (branch_id, service_slug)
);

create table if not exists public.doctors (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  title text not null default 'ד״ר',
  service_slug text not null references public.services on delete restrict,
  bio text not null default '',
  photo_url text,
  languages text[] not null default array['עברית'],
  active boolean not null default true,
  is_sample boolean not null default false
);

create table if not exists public.doctor_schedules (
  id bigint generated always as identity primary key,
  doctor_id uuid not null references public.doctors on delete cascade,
  branch_id uuid not null references public.branches on delete cascade,
  weekday smallint not null check (weekday between 0 and 6), -- 0 = Sunday
  start_time time not null,
  end_time time not null check (end_time > start_time),
  slot_minutes integer not null default 15 check (slot_minutes between 5 and 120)
);
create index if not exists doctor_schedules_lookup on public.doctor_schedules (doctor_id, branch_id, weekday);

alter table public.services enable row level security;
alter table public.branches enable row level security;
alter table public.branch_services enable row level security;
alter table public.doctors enable row level security;
alter table public.doctor_schedules enable row level security;

create policy "public read services" on public.services for select using (true);
create policy "public read branches" on public.branches for select using (active or public.is_staff());
create policy "public read branch services" on public.branch_services for select using (true);
create policy "public read doctors" on public.doctors for select using (active or public.is_staff());
create policy "public read schedules" on public.doctor_schedules for select using (true);

create policy "staff write services" on public.services for all using (public.is_staff()) with check (public.is_staff());
create policy "staff write branches" on public.branches for all using (public.is_staff()) with check (public.is_staff());
create policy "staff write branch services" on public.branch_services for all using (public.is_staff()) with check (public.is_staff());
create policy "staff write doctors" on public.doctors for all using (public.is_staff()) with check (public.is_staff());
create policy "staff write schedules" on public.doctor_schedules for all using (public.is_staff()) with check (public.is_staff());

-- ---------------------------------------------------------------------------
-- Appointments
-- ---------------------------------------------------------------------------
create table if not exists public.appointments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users on delete cascade,
  doctor_id uuid not null references public.doctors on delete restrict,
  branch_id uuid not null references public.branches on delete restrict,
  service_slug text not null references public.services,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  status text not null default 'booked' check (status in ('booked', 'cancelled', 'arrived', 'completed', 'no_show')),
  patient_name text not null,
  patient_phone text not null,
  reason text not null default '',
  created_at timestamptz not null default now()
);
-- A doctor can't have two active appointments at the same time.
create unique index if not exists appointments_no_double_booking
  on public.appointments (doctor_id, starts_at) where status in ('booked', 'arrived');
create index if not exists appointments_user on public.appointments (user_id, starts_at desc);
create index if not exists appointments_branch_day on public.appointments (branch_id, starts_at);

alter table public.appointments enable row level security;
create policy "patients read own, staff read all" on public.appointments for select using (auth.uid() = user_id or public.is_staff());
create policy "staff update appointments" on public.appointments for update using (public.is_staff()) with check (public.is_staff());
-- Patients create and cancel only through the functions below.

-- Free slots for a doctor at a branch on a given (Israel) date.
create or replace function public.available_slots(p_doctor uuid, p_branch uuid, p_day date)
returns table (starts_at timestamptz)
language sql stable security definer set search_path = public as $$
  with sch as (
    select s.start_time, s.end_time, s.slot_minutes
    from public.doctor_schedules s
    join public.doctors d on d.id = s.doctor_id and d.active
    join public.branches b on b.id = s.branch_id and b.active
    where s.doctor_id = p_doctor and s.branch_id = p_branch and s.weekday = extract(dow from p_day)::int
  ),
  slots as (
    select generate_series(
      (p_day + sch.start_time) at time zone 'Asia/Jerusalem',
      (p_day + sch.end_time) at time zone 'Asia/Jerusalem' - make_interval(mins => sch.slot_minutes),
      make_interval(mins => sch.slot_minutes)
    ) as starts_at
    from sch
  )
  select slots.starts_at from slots
  where slots.starts_at > now() + interval '30 minutes'
    and slots.starts_at < now() + interval '60 days'
    and not exists (
      select 1 from public.appointments a
      where a.doctor_id = p_doctor and a.starts_at = slots.starts_at and a.status in ('booked', 'arrived')
    )
  order by 1;
$$;
grant execute on function public.available_slots(uuid, uuid, date) to anon, authenticated;

-- First free slot per doctor/branch for a service over the next p_days days.
create or replace function public.next_available(p_service text, p_branch uuid default null, p_days integer default 14)
returns table (doctor_id uuid, branch_id uuid, first_slot timestamptz)
language sql stable security definer set search_path = public as $$
  select pairs.doctor_id, pairs.branch_id, (
    select s.starts_at
    from generate_series(0, least(p_days, 60) - 1) as g(n)
    cross join lateral public.available_slots(pairs.doctor_id, pairs.branch_id, ((now() at time zone 'Asia/Jerusalem')::date + g.n)) s
    order by s.starts_at
    limit 1
  ) as first_slot
  from (
    select distinct ds.doctor_id, ds.branch_id
    from public.doctor_schedules ds
    join public.doctors d on d.id = ds.doctor_id and d.active and d.service_slug = p_service
    join public.branches b on b.id = ds.branch_id and b.active
    where p_branch is null or ds.branch_id = p_branch
  ) pairs;
$$;
grant execute on function public.next_available(text, uuid, integer) to anon, authenticated;

create or replace function public.book_appointment(
  p_doctor uuid, p_branch uuid, p_starts_at timestamptz,
  p_patient_name text, p_patient_phone text, p_reason text default ''
) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_service text;
  v_minutes integer;
  v_id uuid;
begin
  if v_user is null then raise exception 'not_authenticated'; end if;
  if coalesce(trim(p_patient_name), '') = '' or coalesce(trim(p_patient_phone), '') = '' then
    raise exception 'missing_details';
  end if;
  if not exists (
    select 1 from public.available_slots(p_doctor, p_branch, (p_starts_at at time zone 'Asia/Jerusalem')::date) s
    where s.starts_at = p_starts_at
  ) then
    raise exception 'slot_unavailable';
  end if;
  if (select count(*) from public.appointments where user_id = v_user and status = 'booked' and starts_at > now()) >= 6 then
    raise exception 'too_many_appointments';
  end if;

  select d.service_slug into v_service from public.doctors d where d.id = p_doctor;
  select s.slot_minutes into v_minutes from public.doctor_schedules s
    where s.doctor_id = p_doctor and s.branch_id = p_branch
      and s.weekday = extract(dow from (p_starts_at at time zone 'Asia/Jerusalem'))::int
    limit 1;

  insert into public.appointments (user_id, doctor_id, branch_id, service_slug, starts_at, ends_at, patient_name, patient_phone, reason)
  values (v_user, p_doctor, p_branch, v_service, p_starts_at, p_starts_at + make_interval(mins => coalesce(v_minutes, 15)),
          left(trim(p_patient_name), 80), left(trim(p_patient_phone), 20), left(coalesce(p_reason, ''), 500))
  returning id into v_id;

  update public.profiles set
    full_name = coalesce(nullif(full_name, ''), left(trim(p_patient_name), 80)),
    phone = coalesce(nullif(phone, ''), left(trim(p_patient_phone), 20))
  where id = v_user;
  return v_id;
exception
  when unique_violation then raise exception 'slot_unavailable';
end;
$$;
grant execute on function public.book_appointment(uuid, uuid, timestamptz, text, text, text) to authenticated;

create or replace function public.cancel_appointment(p_id uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
  update public.appointments set status = 'cancelled'
  where id = p_id and user_id = auth.uid() and status = 'booked' and starts_at > now();
  if not found then raise exception 'cannot_cancel'; end if;
end;
$$;
grant execute on function public.cancel_appointment(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Contact messages & site settings
-- ---------------------------------------------------------------------------
create table if not exists public.contact_messages (
  id bigint generated always as identity primary key,
  name text not null check (length(name) between 2 and 80),
  phone text check (phone is null or length(phone) <= 20),
  email text check (email is null or length(email) <= 120),
  subject text not null default 'כללי',
  message text not null check (length(message) between 5 and 2000),
  handled boolean not null default false,
  created_at timestamptz not null default now()
);
alter table public.contact_messages enable row level security;
create policy "anyone can write to us" on public.contact_messages for insert with check (true);
create policy "staff read messages" on public.contact_messages for select using (public.is_staff());
create policy "staff update messages" on public.contact_messages for update using (public.is_staff());

create table if not exists public.site_settings (
  key text primary key,
  value text not null default ''
);
alter table public.site_settings enable row level security;
create policy "public read settings" on public.site_settings for select using (true);
create policy "staff write settings" on public.site_settings for all using (public.is_staff()) with check (public.is_staff());
