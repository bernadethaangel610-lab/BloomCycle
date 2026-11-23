create table if not exists public.bloom_profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    name varchar(120) not null,
    age smallint not null check (age between 9 and 120),
    role text not null default 'user' check (role in ('user', 'admin')),
    is_disabled boolean not null default false,
    created_at timestamptz not null default now()
);

create table if not exists public.bloom_health_data (
    user_id uuid primary key references public.bloom_profiles(id) on delete cascade,
    payload jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);

create table if not exists public.bloom_discussion_posts (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.bloom_profiles(id) on delete cascade,
    title varchar(240) not null,
    category varchar(40) not null check (category in ('Kesehatan', 'Tips', 'Pengalaman')),
    body text not null,
    is_hidden boolean not null default false,
    created_at timestamptz not null default now()
);

create table if not exists public.bloom_discussion_answers (
    id uuid primary key default gen_random_uuid(),
    discussion_id uuid not null references public.bloom_discussion_posts(id) on delete cascade,
    user_id uuid not null references public.bloom_profiles(id) on delete cascade,
    body text not null,
    is_hidden boolean not null default false,
    created_at timestamptz not null default now()
);

create table if not exists public.bloom_article_comments (
    id uuid primary key default gen_random_uuid(),
    article_id smallint not null check (article_id between 0 and 2),
    user_id uuid not null references public.bloom_profiles(id) on delete cascade,
    body text not null,
    is_hidden boolean not null default false,
    created_at timestamptz not null default now()
);

create table if not exists public.bloom_reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references public.bloom_profiles(id) on delete cascade,
    target_type varchar(32) not null,
    target_id text,
    target_title varchar(240) not null,
    category varchar(80) not null,
    description text not null,
    status varchar(20) not null default 'open' check (status in ('open', 'resolved', 'dismissed')),
    created_at timestamptz not null default now()
);

create table if not exists public.bloom_app_settings (
    setting_key varchar(64) primary key,
    setting_value text not null,
    updated_at timestamptz not null default now()
);

insert into public.bloom_app_settings (setting_key, setting_value)
values ('announcement', ''), ('community_enabled', '1')
on conflict (setting_key) do nothing;

create or replace function public.bloom_create_profile()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, auth
as $$
declare
    profile_age smallint;
begin
    if coalesce(new.raw_user_meta_data ->> 'age', '') ~ '^[0-9]{1,3}$' then
        profile_age := (new.raw_user_meta_data ->> 'age')::smallint;
    else
        profile_age := 18;
    end if;
    if profile_age not between 9 and 120 then
        profile_age := 18;
    end if;

    insert into public.bloom_profiles (id, name, age)
    values (
        new.id,
        coalesce(nullif(trim(new.raw_user_meta_data ->> 'name'), ''), split_part(new.email, '@', 1)),
        profile_age
    )
    on conflict (id) do update
    set name = excluded.name, age = excluded.age;
    return new;
end;
$$;

drop trigger if exists bloom_auth_user_created on auth.users;
create trigger bloom_auth_user_created
after insert on auth.users
for each row execute function public.bloom_create_profile();

insert into public.bloom_profiles (id, name, age)
select
    u.id,
    coalesce(nullif(trim(u.raw_user_meta_data ->> 'name'), ''), split_part(u.email, '@', 1)),
    case
        when coalesce(u.raw_user_meta_data ->> 'age', '') ~ '^[0-9]{1,3}$'
         and (u.raw_user_meta_data ->> 'age')::integer between 9 and 120
        then (u.raw_user_meta_data ->> 'age')::smallint
        else 18
    end
from auth.users u
on conflict (id) do nothing;

create or replace function public.bloom_is_admin()
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select exists (
        select 1
        from public.bloom_profiles p
        where p.id = auth.uid()
          and p.role = 'admin'
          and not p.is_disabled
    );
$$;

create or replace function public.bloom_valid_date(p_value text)
returns boolean
language plpgsql
stable
set search_path = pg_temp
as $$
begin
    if p_value is null then
        return false;
    end if;
    return p_value ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
       and to_char(to_date(p_value, 'FXYYYY-MM-DD'), 'YYYY-MM-DD') = p_value;
exception when others then
    return false;
end;
$$;

alter table public.bloom_profiles enable row level security;
alter table public.bloom_health_data enable row level security;
alter table public.bloom_discussion_posts enable row level security;
alter table public.bloom_discussion_answers enable row level security;
alter table public.bloom_article_comments enable row level security;
alter table public.bloom_reports enable row level security;
alter table public.bloom_app_settings enable row level security;

drop policy if exists bloom_profiles_select_self on public.bloom_profiles;
create policy bloom_profiles_select_self on public.bloom_profiles
for select to authenticated using (id = auth.uid());

drop policy if exists bloom_health_select_self on public.bloom_health_data;
create policy bloom_health_select_self on public.bloom_health_data
for select to authenticated using (user_id = auth.uid());
drop policy if exists bloom_health_insert_self on public.bloom_health_data;
create policy bloom_health_insert_self on public.bloom_health_data
for insert to authenticated with check (user_id = auth.uid());
drop policy if exists bloom_health_update_self on public.bloom_health_data;
create policy bloom_health_update_self on public.bloom_health_data
for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists bloom_posts_read_visible on public.bloom_discussion_posts;
create policy bloom_posts_read_visible on public.bloom_discussion_posts
for select to authenticated using (not is_hidden or public.bloom_is_admin());
drop policy if exists bloom_posts_insert_self on public.bloom_discussion_posts;
create policy bloom_posts_insert_self on public.bloom_discussion_posts
for insert to authenticated with check (user_id = auth.uid());

drop policy if exists bloom_answers_read_visible on public.bloom_discussion_answers;
create policy bloom_answers_read_visible on public.bloom_discussion_answers
for select to authenticated using (not is_hidden or public.bloom_is_admin());
drop policy if exists bloom_answers_insert_self on public.bloom_discussion_answers;
create policy bloom_answers_insert_self on public.bloom_discussion_answers
for insert to authenticated with check (user_id = auth.uid());

drop policy if exists bloom_comments_read_visible on public.bloom_article_comments;
create policy bloom_comments_read_visible on public.bloom_article_comments
for select to authenticated using (not is_hidden or public.bloom_is_admin());
drop policy if exists bloom_comments_insert_self on public.bloom_article_comments;
create policy bloom_comments_insert_self on public.bloom_article_comments
for insert to authenticated with check (user_id = auth.uid());

drop policy if exists bloom_reports_insert_self on public.bloom_reports;
create policy bloom_reports_insert_self on public.bloom_reports
for insert to authenticated with check (reporter_id = auth.uid());
drop policy if exists bloom_reports_admin_read on public.bloom_reports;
create policy bloom_reports_admin_read on public.bloom_reports
for select to authenticated using (public.bloom_is_admin());
drop policy if exists bloom_reports_admin_update on public.bloom_reports;
create policy bloom_reports_admin_update on public.bloom_reports
for update to authenticated using (public.bloom_is_admin()) with check (public.bloom_is_admin());

drop policy if exists bloom_settings_read_authenticated on public.bloom_app_settings;
create policy bloom_settings_read_authenticated on public.bloom_app_settings
for select to authenticated using (true);
drop policy if exists bloom_settings_admin_write on public.bloom_app_settings;
create policy bloom_settings_admin_write on public.bloom_app_settings
for all to authenticated using (public.bloom_is_admin()) with check (public.bloom_is_admin());

revoke all on public.bloom_profiles from public, anon, authenticated;
revoke all on public.bloom_health_data from public, anon, authenticated;
revoke all on public.bloom_discussion_posts from public, anon, authenticated;
revoke all on public.bloom_discussion_answers from public, anon, authenticated;
revoke all on public.bloom_article_comments from public, anon, authenticated;
revoke all on public.bloom_reports from public, anon, authenticated;
revoke all on public.bloom_app_settings from public, anon, authenticated;
revoke all on function public.bloom_create_profile() from public, anon, authenticated;
revoke all on function public.bloom_valid_date(text) from public, anon, authenticated;
revoke all on function public.bloom_is_admin() from public, anon;
grant usage on schema public to authenticated;
grant execute on function public.bloom_is_admin() to authenticated;

create or replace function public.bloom_api(
    p_route text,
    p_method text default 'GET',
    p_body jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, public, auth
as $$
declare
    caller_id uuid := auth.uid();
    caller_profile public.bloom_profiles%rowtype;
    post_id uuid;
    answer_id uuid;
    result jsonb;
    community_enabled boolean;
    announcement text;
    body_text text;
begin
    if caller_id is null then
        raise exception using errcode = '28000', message = 'Please sign in again.';
    end if;

    select * into caller_profile
    from public.bloom_profiles
    where id = caller_id;

    if not found or caller_profile.is_disabled then
        raise exception using errcode = '42501', message = 'This account has been disabled or is unavailable.';
    end if;

    select coalesce((select setting_value = '1' from public.bloom_app_settings
                     where setting_key = 'community_enabled'), false)
    into community_enabled;
    select coalesce((select setting_value from public.bloom_app_settings
                     where setting_key = 'announcement'), '')
    into announcement;

    if p_route = 'bootstrap' and p_method = 'GET' then
        select jsonb_build_object(
            'user', jsonb_build_object(
                'id', caller_profile.id,
                'name', caller_profile.name,
                'age', caller_profile.age,
                'email', (select email from auth.users where id = caller_id),
                'role', caller_profile.role
            ),
            'health', (select payload from public.bloom_health_data where user_id = caller_id),
            'discussions', case when community_enabled then coalesce((
                select jsonb_agg(
                    jsonb_build_object(
                        'id', p.id,
                        'userId', p.user_id,
                        'title', p.title,
                        'cat', p.category,
                        'text', p.body,
                        'isHidden', p.is_hidden,
                        'authorName', author.name,
                        'authorEmail', '',
                        'date', to_char(p.created_at at time zone 'UTC', 'YYYY-MM-DD'),
                        'answers', coalesce((
                            select jsonb_agg(jsonb_build_object(
                                'id', a.id,
                                'userId', a.user_id,
                                'authorName', answer_author.name,
                                'authorEmail', '',
                                'date', to_char(a.created_at at time zone 'UTC', 'YYYY-MM-DD'),
                                'text', a.body,
                                'isHidden', a.is_hidden
                            ) order by a.created_at)
                            from public.bloom_discussion_answers a
                            join public.bloom_profiles answer_author on answer_author.id = a.user_id
                            where a.discussion_id = p.id and not a.is_hidden
                        ), '[]'::jsonb)
                    ) order by p.created_at desc
                )
                from (
                    select * from public.bloom_discussion_posts
                    where not is_hidden
                    order by created_at desc
                    limit 500
                ) p
                join public.bloom_profiles author on author.id = p.user_id
            ), '[]'::jsonb) else '[]'::jsonb end,
            'articleComments', coalesce((
                select jsonb_agg(jsonb_build_object(
                    'id', c.id,
                    'articleId', c.article_id,
                    'userId', c.user_id,
                    'name', author.name,
                    'email', '',
                    'date', to_char(c.created_at at time zone 'UTC', 'YYYY-MM-DD'),
                    'text', c.body
                ) order by c.created_at desc)
                from (
                    select * from public.bloom_article_comments
                    where not is_hidden
                    order by created_at desc
                    limit 1000
                ) c
                join public.bloom_profiles author on author.id = c.user_id
            ), '[]'::jsonb),
            'settings', jsonb_build_object(
                'announcement', announcement,
                'communityEnabled', community_enabled
            )
        ) into result;
        return result;
    end if;

    if p_route = 'health' and p_method = 'PUT' then
        if jsonb_typeof(p_body -> 'periods') is distinct from 'array'
           or jsonb_typeof(p_body -> 'notes') is distinct from 'array' then
            raise exception using errcode = '22023', message = 'Health data is invalid or exceeds the supported size.';
        end if;
        if jsonb_array_length(p_body -> 'periods') > 500
           or jsonb_array_length(p_body -> 'notes') > 2000 then
            raise exception using errcode = '22023', message = 'Health data is invalid or exceeds the supported size.';
        end if;

        if exists (
            select 1 from jsonb_array_elements(p_body -> 'periods') item
            where not public.bloom_valid_date(item ->> 's')
               or (item ->> 'e' is not null and not public.bloom_valid_date(item ->> 'e'))
        ) or exists (
            select 1 from jsonb_array_elements(p_body -> 'notes') item
            where not public.bloom_valid_date(item ->> 'date')
        ) then
            raise exception using errcode = '22023', message = 'A health record contains an invalid date.';
        end if;

        if p_body ->> 'theme' not in ('auto', 'light', 'dark') then
            p_body := jsonb_set(p_body, '{theme}', '"auto"'::jsonb, true);
        end if;

        insert into public.bloom_health_data (user_id, payload, updated_at)
        values (caller_id, p_body, now())
        on conflict (user_id) do update
        set payload = excluded.payload, updated_at = excluded.updated_at;
        return jsonb_build_object('message', 'Health data saved.');
    end if;

    if p_route = 'discussions' and p_method = 'POST' then
        if not community_enabled then
            raise exception using errcode = '42501', message = 'Community features are temporarily disabled.';
        end if;
        body_text := trim(coalesce(p_body ->> 'body', ''));
        if length(trim(coalesce(p_body ->> 'title', ''))) not between 1 and 240
           or length(body_text) not between 1 and 10000
           or coalesce(p_body ->> 'category', '') not in ('Kesehatan', 'Tips', 'Pengalaman') then
            raise exception using errcode = '22023', message = 'Enter a valid title, category, and discussion text.';
        end if;
        insert into public.bloom_discussion_posts (user_id, title, category, body)
        values (caller_id, trim(p_body ->> 'title'), p_body ->> 'category', body_text)
        returning id into post_id;
        return jsonb_build_object('id', post_id);
    end if;

    if p_route ~ '^discussions/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/answers$'
       and p_method = 'POST' then
        if not community_enabled then
            raise exception using errcode = '42501', message = 'Community features are temporarily disabled.';
        end if;
        post_id := substring(p_route from '^discussions/([0-9a-fA-F-]+)/answers$')::uuid;
        body_text := trim(coalesce(p_body ->> 'body', ''));
        if length(body_text) not between 1 and 10000 then
            raise exception using errcode = '22023', message = 'Answer text is required and must be under 10,000 characters.';
        end if;
        if not exists (select 1 from public.bloom_discussion_posts where id = post_id and not is_hidden) then
            raise exception using errcode = 'P0002', message = 'Discussion not found.';
        end if;
        insert into public.bloom_discussion_answers (discussion_id, user_id, body)
        values (post_id, caller_id, body_text)
        returning id into answer_id;
        return jsonb_build_object('id', answer_id);
    end if;

    if p_route = 'article-comments' and p_method = 'POST' then
        body_text := trim(coalesce(p_body ->> 'body', ''));
        if coalesce(p_body ->> 'articleId', '') !~ '^[0-2]$'
           or length(body_text) not between 1 and 5000 then
            raise exception using errcode = '22023', message = 'Article comment is invalid.';
        end if;
        insert into public.bloom_article_comments (article_id, user_id, body)
        values ((p_body ->> 'articleId')::smallint, caller_id, body_text)
        returning id into answer_id;
        return jsonb_build_object('id', answer_id);
    end if;

    if p_route = 'reports' and p_method = 'POST' then
        body_text := trim(coalesce(p_body ->> 'description', ''));
        if length(trim(coalesce(p_body ->> 'targetTitle', ''))) not between 1 and 240
           or length(trim(coalesce(p_body ->> 'category', ''))) not between 1 and 80
           or length(body_text) not between 1 and 5000
           or coalesce(p_body ->> 'targetType', 'other') not in ('discussion', 'answer', 'article_comment', 'other') then
            raise exception using errcode = '22023', message = 'Report details are invalid.';
        end if;
        insert into public.bloom_reports (
            reporter_id, target_type, target_id, target_title, category, description
        ) values (
            caller_id,
            coalesce(p_body ->> 'targetType', 'other'),
            p_body ->> 'targetId',
            trim(p_body ->> 'targetTitle'),
            trim(p_body ->> 'category'),
            body_text
        );
        return jsonb_build_object('message', 'Report submitted.');
    end if;

    if p_route = 'admin/reports' and p_method = 'GET' then
        if caller_profile.role <> 'admin' then
            raise exception using errcode = '42501', message = 'Administrator access is required.';
        end if;
        select jsonb_build_object('reports', coalesce(jsonb_agg(jsonb_build_object(
            'id', r.id,
            'targetType', r.target_type,
            'targetId', r.target_id,
            'targetTitle', r.target_title,
            'category', r.category,
            'description', r.description,
            'status', r.status,
            'createdAt', to_char(r.created_at at time zone 'UTC', 'YYYY-MM-DD HH24:MI'),
            'reporterName', p.name,
            'reporterEmail', coalesce(u.email, '')
        ) order by r.created_at desc), '[]'::jsonb))
        into result
        from (
            select * from public.bloom_reports
            order by created_at desc
            limit 500
        ) r
        join public.bloom_profiles p on p.id = r.reporter_id
        left join auth.users u on u.id = r.reporter_id;
        return result;
    end if;

    if p_route = 'admin/discussions' and p_method = 'GET' then
        if caller_profile.role <> 'admin' then
            raise exception using errcode = '42501', message = 'Administrator access is required.';
        end if;
        select jsonb_build_object('discussions', coalesce(jsonb_agg(
            jsonb_build_object(
                'id', p.id,
                'userId', p.user_id,
                'title', p.title,
                'cat', p.category,
                'text', p.body,
                'isHidden', p.is_hidden,
                'authorName', author.name,
                'authorEmail', '',
                'date', to_char(p.created_at at time zone 'UTC', 'YYYY-MM-DD'),
                'answers', coalesce((
                    select jsonb_agg(jsonb_build_object(
                        'id', a.id,
                        'userId', a.user_id,
                        'authorName', answer_author.name,
                        'authorEmail', '',
                        'date', to_char(a.created_at at time zone 'UTC', 'YYYY-MM-DD'),
                        'text', a.body,
                        'isHidden', a.is_hidden
                    ) order by a.created_at)
                    from public.bloom_discussion_answers a
                    join public.bloom_profiles answer_author on answer_author.id = a.user_id
                    where a.discussion_id = p.id
                ), '[]'::jsonb)
            ) order by p.created_at desc
        ), '[]'::jsonb))
        into result
        from (
            select * from public.bloom_discussion_posts
            order by created_at desc
            limit 500
        ) p
        join public.bloom_profiles author on author.id = p.user_id;
        return result;
    end if;

    raise exception using errcode = 'P0002', message = 'Requested path is invalid.';
end;
$$;

revoke all on function public.bloom_api(text, text, jsonb) from public, anon;
grant execute on function public.bloom_api(text, text, jsonb) to authenticated;

do $$
declare
    legacy_policy record;
begin
    for legacy_policy in
        select schemaname, tablename, policyname
        from pg_policies
        where schemaname = 'public'
          and policyname in (
              'Allow public read-write for BloomCycle app',
              'Allow public read/write users',
              'Allow public read/write periods',
              'Allow public read/write notes',
              'Allow public read/write discussions',
              'Allow public read/write answers',
              'Allow public read/write comments',
              'Allow public read/write reports',
              'Allow anonymous & authenticated access for BloomCycle application'
          )
    loop
        execute format('drop policy %I on %I.%I',
            legacy_policy.policyname, legacy_policy.schemaname, legacy_policy.tablename);
    end loop;
end;
$$;

notify pgrst, 'reload schema';
