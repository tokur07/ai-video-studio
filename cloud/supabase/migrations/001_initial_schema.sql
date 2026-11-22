create table if not exists public.projects (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    title text not null,
    topic text not null default '',
    video_type text not null default 'documentary',
    production_mode text not null default 'automatic',
    aspect_ratio text not null default '16:9',
    status text not null default 'draft',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.projects enable row level security;

create policy "Users view own projects"
on public.projects for select using (auth.uid() = user_id);

create policy "Users create own projects"
on public.projects for insert with check (auth.uid() = user_id);

create policy "Users update own projects"
on public.projects for update
using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "Users delete own projects"
on public.projects for delete using (auth.uid() = user_id);
