-- App-specific cloud metadata table.
-- Kept separate from the UUID-based public.projects schema so Android Long IDs
-- cannot collide with the existing web/backend project model.
create table if not exists public.studio_projects (
    project_id text not null,
    user_id uuid not null references auth.users(id) on delete cascade,
    title text not null default '',
    project_type text not null default '',
    production_mode text not null default '',
    r2_prefix text not null default '',
    updated_at timestamptz not null default now(),
    primary key (user_id, project_id)
);

alter table public.studio_projects enable row level security;

drop policy if exists "Studio users view own projects" on public.studio_projects;
create policy "Studio users view own projects"
on public.studio_projects for select
using (auth.uid() = user_id);

drop policy if exists "Studio users create own projects" on public.studio_projects;
create policy "Studio users create own projects"
on public.studio_projects for insert
with check (auth.uid() = user_id);

drop policy if exists "Studio users update own projects" on public.studio_projects;
create policy "Studio users update own projects"
on public.studio_projects for update
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists "Studio users delete own projects" on public.studio_projects;
create policy "Studio users delete own projects"
on public.studio_projects for delete
using (auth.uid() = user_id);
