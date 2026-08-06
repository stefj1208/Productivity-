-- =============================================================
-- Notre Semaine — schéma de la base Supabase
-- À coller tel quel dans : Supabase > SQL Editor > New query > Run
-- =============================================================

create extension if not exists pgcrypto;

-- ---------- Tables ----------

create table if not exists couples (
  id uuid primary key default gen_random_uuid(),
  code text unique not null,
  created_at timestamptz default now()
);

create table if not exists profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null default '',
  color text not null default 'A',
  couple_id uuid references couples(id),
  updated_at bigint not null default 0
);

create table if not exists tasks (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  couple_id uuid,
  title text not null default '',
  date text,
  week_start text,
  is_priority boolean not null default false,
  is_sport boolean not null default false,
  done boolean not null default false,
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

create table if not exists day_plans (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  couple_id uuid,
  date text not null,
  wake_time text,
  focus_blocks text,
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

create table if not exists week_plans (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  couple_id uuid,
  week_start text not null,
  priority text,
  abandon text,
  validated_at bigint,
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

create table if not exists encouragements (
  id text primary key,
  from_user uuid not null references auth.users(id) on delete cascade,
  to_user uuid not null,
  couple_id uuid,
  date text not null,
  message text not null default '',
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

create table if not exists goals (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  couple_id uuid,
  title text not null default '',
  domain text not null default 'autre',
  sessions_per_week int not null default 3,
  minutes_per_session int not null default 30,
  preferred_time text not null default 'soir',
  preferred_days text not null default '',
  next_action text not null default '',
  is_private boolean not null default false,
  active boolean not null default true,
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

create table if not exists ritual_logs (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  couple_id uuid,
  date text not null,
  minutes int not null default 0,
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

create table if not exists usage_days (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  couple_id uuid,
  date text not null,
  total_minutes int not null default 0,
  social_minutes int not null default 0,
  unlocks int not null default 0,
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

create table if not exists grace_requests (
  id text primary key,
  from_user uuid not null references auth.users(id) on delete cascade,
  to_user uuid not null,
  couple_id uuid,
  date text not null,
  minutes int not null default 15,
  status text not null default 'pending',
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

-- Mise à jour depuis la V1 (sans effet sur une base neuve)
alter table tasks add column if not exists goal_id text;

-- ---------- Fonctions ----------

-- Le couple de la personne connectée (contourne proprement la récursion RLS).
create or replace function my_couple() returns uuid
language sql security definer stable
set search_path = public
as $$
  select couple_id from public.profiles where id = auth.uid();
$$;

-- Renseigne automatiquement couple_id sur chaque ligne envoyée.
create or replace function stamp_couple() returns trigger
language plpgsql security definer
set search_path = public
as $$
begin
  new.couple_id := (select couple_id from public.profiles where id = auth.uid());
  return new;
end;
$$;

create or replace trigger tasks_stamp before insert or update on tasks
  for each row execute function stamp_couple();
create or replace trigger day_plans_stamp before insert or update on day_plans
  for each row execute function stamp_couple();
create or replace trigger week_plans_stamp before insert or update on week_plans
  for each row execute function stamp_couple();
create or replace trigger encouragements_stamp before insert or update on encouragements
  for each row execute function stamp_couple();
create or replace trigger goals_stamp before insert or update on goals
  for each row execute function stamp_couple();
create or replace trigger ritual_logs_stamp before insert or update on ritual_logs
  for each row execute function stamp_couple();
create or replace trigger usage_days_stamp before insert or update on usage_days
  for each row execute function stamp_couple();
create or replace trigger grace_requests_stamp before insert or update on grace_requests
  for each row execute function stamp_couple();

-- Crée l'espace couple et renvoie le code à partager (6 caractères).
create or replace function create_couple() returns text
language plpgsql security definer
set search_path = public
as $$
declare
  v_code text;
  v_id uuid;
begin
  insert into profiles (id) values (auth.uid()) on conflict (id) do nothing;
  v_code := upper(substr(md5(random()::text), 1, 6));
  insert into couples (code) values (v_code) returning id into v_id;
  update profiles set couple_id = v_id where id = auth.uid();
  return v_code;
end;
$$;

-- Rejoint l'espace couple correspondant au code.
create or replace function join_couple(p_code text) returns void
language plpgsql security definer
set search_path = public
as $$
declare
  v_id uuid;
begin
  insert into profiles (id) values (auth.uid()) on conflict (id) do nothing;
  select id into v_id from couples where code = upper(trim(p_code));
  if v_id is null then
    raise exception 'Code inconnu';
  end if;
  update profiles set couple_id = v_id where id = auth.uid();
end;
$$;

-- ---------- Sécurité : seules les deux personnes du couple voient les données ----------

alter table couples enable row level security;         -- aucun accès direct
alter table profiles enable row level security;
alter table tasks enable row level security;
alter table day_plans enable row level security;
alter table week_plans enable row level security;
alter table encouragements enable row level security;

-- Ré-exécutable sans erreur : on supprime les règles avant de les recréer.
drop policy if exists profiles_select on profiles;
drop policy if exists profiles_insert on profiles;
drop policy if exists profiles_update on profiles;
drop policy if exists tasks_select on tasks;
drop policy if exists tasks_insert on tasks;
drop policy if exists tasks_update on tasks;
drop policy if exists day_plans_select on day_plans;
drop policy if exists day_plans_insert on day_plans;
drop policy if exists day_plans_update on day_plans;
drop policy if exists week_plans_select on week_plans;
drop policy if exists week_plans_insert on week_plans;
drop policy if exists week_plans_update on week_plans;
drop policy if exists encouragements_select on encouragements;
drop policy if exists encouragements_insert on encouragements;
drop policy if exists encouragements_update on encouragements;
drop policy if exists goals_select on goals;
drop policy if exists goals_insert on goals;
drop policy if exists goals_update on goals;
drop policy if exists ritual_logs_select on ritual_logs;
drop policy if exists ritual_logs_insert on ritual_logs;
drop policy if exists ritual_logs_update on ritual_logs;
drop policy if exists usage_days_select on usage_days;
drop policy if exists usage_days_insert on usage_days;
drop policy if exists usage_days_update on usage_days;
drop policy if exists grace_select on grace_requests;
drop policy if exists grace_insert on grace_requests;
drop policy if exists grace_update on grace_requests;

create policy profiles_select on profiles for select
  using (id = auth.uid() or (couple_id is not null and couple_id = my_couple()));
create policy profiles_insert on profiles for insert
  with check (id = auth.uid());
create policy profiles_update on profiles for update
  using (id = auth.uid());

create policy tasks_select on tasks for select
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));
create policy tasks_insert on tasks for insert
  with check (user_id = auth.uid());
create policy tasks_update on tasks for update
  using (user_id = auth.uid());

create policy day_plans_select on day_plans for select
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));
create policy day_plans_insert on day_plans for insert
  with check (user_id = auth.uid());
create policy day_plans_update on day_plans for update
  using (user_id = auth.uid());

create policy week_plans_select on week_plans for select
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));
create policy week_plans_insert on week_plans for insert
  with check (user_id = auth.uid());
create policy week_plans_update on week_plans for update
  using (user_id = auth.uid());

create policy encouragements_select on encouragements for select
  using (from_user = auth.uid() or to_user = auth.uid()
         or (couple_id is not null and couple_id = my_couple()));
create policy encouragements_insert on encouragements for insert
  with check (from_user = auth.uid());
create policy encouragements_update on encouragements for update
  using (from_user = auth.uid());

alter table goals enable row level security;
alter table ritual_logs enable row level security;
alter table usage_days enable row level security;
alter table grace_requests enable row level security;

-- Un objectif « privé » n'est jamais visible par le partenaire.
create policy goals_select on goals for select
  using (user_id = auth.uid()
         or (couple_id is not null and couple_id = my_couple() and is_private = false));
create policy goals_insert on goals for insert
  with check (user_id = auth.uid());
create policy goals_update on goals for update
  using (user_id = auth.uid());

create policy ritual_logs_select on ritual_logs for select
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));
create policy ritual_logs_insert on ritual_logs for insert
  with check (user_id = auth.uid());
create policy ritual_logs_update on ritual_logs for update
  using (user_id = auth.uid());

create policy usage_days_select on usage_days for select
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));
create policy usage_days_insert on usage_days for insert
  with check (user_id = auth.uid());
create policy usage_days_update on usage_days for update
  using (user_id = auth.uid());

-- La demande est créée par celui qui a dépassé ; la réponse vient du partenaire.
create policy grace_select on grace_requests for select
  using (from_user = auth.uid() or to_user = auth.uid()
         or (couple_id is not null and couple_id = my_couple()));
create policy grace_insert on grace_requests for insert
  with check (from_user = auth.uid());
create policy grace_update on grace_requests for update
  using (from_user = auth.uid() or to_user = auth.uid());
