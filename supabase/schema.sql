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
