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

create table if not exists meals (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  couple_id uuid,
  date text not null,
  slot text not null,
  title text not null default '',
  ingredients text not null default '',
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

create table if not exists shopping_items (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  couple_id uuid,
  week_start text not null,
  label text not null default '',
  aisle text not null default 'Divers',
  checked boolean not null default false,
  manual boolean not null default false,
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

create table if not exists health_days (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  couple_id uuid,
  date text not null,
  sleep_minutes int not null default 0,
  steps int not null default 0,
  exercise_minutes int not null default 0,
  source text not null default 'manuel',
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

-- Mise à jour depuis la V1 (sans effet sur une base neuve)
alter table tasks add column if not exists goal_id text;

-- Réglages du Pacte, partagés dans le couple (V4)
alter table tasks add column if not exists assigned_by text not null default '';
alter table tasks add column if not exists start_time text not null default '';
alter table tasks add column if not exists duration_minutes int not null default 0;

-- Finance et enfants : ce qui se gère à deux, hors repas.
create table if not exists house_items (
  id text primary key,
  couple_id uuid references couples(id) on delete cascade,
  section text not null,
  title text not null,
  detail text not null default '',
  amount double precision not null default 0,
  due_date text,
  done boolean not null default false,
  deleted boolean not null default false,
  updated_at bigint not null
);
-- Menus (V10) : ce qu'il y a dans l'assiette de chacun, et les calories
alter table meals add column if not exists quantities text not null default '';
alter table meals add column if not exists calories int not null default 0;

-- Poids (V10). Donnée de santé : elle n'est envoyée que si son propriétaire
-- a coché le partage dans l'application. La règle RLS reste celle du couple.
create table if not exists weights (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  couple_id uuid,
  date text not null,
  kilos double precision not null,
  note text not null default '',
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

-- Habitudes (V12) : personnelles, mais synchronisées entre vos appareils.
create table if not exists habits (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  couple_id uuid,
  title text not null,
  source text not null default '',
  enabled boolean not null default true,
  from_hour int not null default 8,
  to_hour int not null default 21,
  per_day int not null default 2,
  deleted boolean not null default false,
  updated_at bigint not null default 0
);

alter table profiles add column if not exists pacte_enabled boolean not null default false;
alter table profiles add column if not exists daily_limit_minutes int not null default 45;
alter table profiles add column if not exists curfew_enabled boolean not null default false;
alter table profiles add column if not exists curfew_start text not null default '22:30';
alter table profiles add column if not exists curfew_end text not null default '06:30';

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
create or replace trigger meals_stamp before insert or update on meals
  for each row execute function stamp_couple();
create or replace trigger shopping_items_stamp before insert or update on shopping_items
  for each row execute function stamp_couple();
create or replace trigger health_days_stamp before insert or update on health_days
  for each row execute function stamp_couple();
create or replace trigger weights_stamp before insert or update on weights
  for each row execute function stamp_couple();
create or replace trigger habits_stamp before insert or update on habits
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
alter table house_items enable row level security;

drop trigger if exists house_items_couple on house_items;
create trigger house_items_couple before insert or update on house_items
  for each row execute function stamp_couple();

drop policy if exists house_items_select on house_items;
create policy house_items_select on house_items for select
  using (couple_id = my_couple());
drop policy if exists house_items_insert on house_items;
create policy house_items_insert on house_items for insert
  with check (true);
drop policy if exists house_items_update on house_items;
create policy house_items_update on house_items for update
  using (couple_id = my_couple());

drop policy if exists meals_select on meals;
drop policy if exists meals_insert on meals;
drop policy if exists meals_update on meals;
drop policy if exists shopping_select on shopping_items;
drop policy if exists shopping_insert on shopping_items;
drop policy if exists shopping_update on shopping_items;
drop policy if exists health_select on health_days;
drop policy if exists health_insert on health_days;
drop policy if exists health_update on health_days;

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

alter table meals enable row level security;
alter table shopping_items enable row level security;
alter table health_days enable row level security;

-- Menus et courses sont communs : les deux peuvent les modifier.
create policy meals_select on meals for select
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));
create policy meals_insert on meals for insert
  with check (user_id = auth.uid());
create policy meals_update on meals for update
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));

create policy shopping_select on shopping_items for select
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));
create policy shopping_insert on shopping_items for insert
  with check (user_id = auth.uid());
create policy shopping_update on shopping_items for update
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));

-- Les données de santé restent modifiables par leur seul propriétaire.
create policy health_select on health_days for select
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));
create policy health_insert on health_days for insert
  with check (user_id = auth.uid());
create policy health_update on health_days for update
  using (user_id = auth.uid());

-- Poids : comme la santé, écrit uniquement par son propriétaire.
alter table weights enable row level security;
drop policy if exists weights_select on weights;
drop policy if exists weights_insert on weights;
drop policy if exists weights_update on weights;
create policy weights_select on weights for select
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));
create policy weights_insert on weights for insert
  with check (user_id = auth.uid());
create policy weights_update on weights for update
  using (user_id = auth.uid());

-- Habitudes : lues par le couple, écrites par leur seul propriétaire.
alter table habits enable row level security;
drop policy if exists habits_select on habits;
drop policy if exists habits_insert on habits;
drop policy if exists habits_update on habits;
create policy habits_select on habits for select
  using (user_id = auth.uid() or (couple_id is not null and couple_id = my_couple()));
create policy habits_insert on habits for insert
  with check (user_id = auth.uid());
create policy habits_update on habits for update
  using (user_id = auth.uid());
