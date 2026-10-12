-- Trigram index support for fast case-insensitive "contains" search on task titles.
create extension if not exists pg_trgm;

create table users (
    id                  bigserial primary key,
    email               varchar(255) not null unique,   -- always stored lower-cased
    password_hash       varchar(100) not null,
    full_name           varchar(100) not null,
    role                varchar(20)  not null default 'USER',
    enabled             boolean      not null default true,
    email_notifications boolean      not null default true,
    avatar_color        varchar(7)   not null default '#6366f1',
    created_at          timestamptz  not null default now()
);

-- Refresh tokens are opaque random strings; only their SHA-256 hash is stored.
-- family_id groups a chain of rotated tokens so reuse of an old token revokes the whole chain.
create table refresh_tokens (
    id          bigserial primary key,
    user_id     bigint      not null references users (id) on delete cascade,
    token_hash  varchar(64) not null unique,
    family_id   uuid        not null,
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    rotated     boolean     not null default false,  -- revoked because it was exchanged (vs. logout/theft)
    created_at  timestamptz not null default now()
);
create index idx_refresh_tokens_family on refresh_tokens (family_id);
create index idx_refresh_tokens_user on refresh_tokens (user_id);

create table projects (
    id          bigserial primary key,
    project_key varchar(10)  not null unique,
    name        varchar(100) not null,
    description text,
    owner_id    bigint       not null references users (id),
    task_seq    int          not null default 0,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    version     bigint       not null default 0
);

create table project_members (
    id         bigserial primary key,
    project_id bigint      not null references projects (id) on delete cascade,
    user_id    bigint      not null references users (id) on delete cascade,
    role       varchar(20) not null,
    joined_at  timestamptz not null default now(),
    unique (project_id, user_id)
);
create index idx_project_members_user on project_members (user_id);

-- "position" columns hold fractional-index keys. They MUST compare byte-wise,
-- so they use the "C" collation (a locale collation would ignore case and break ordering).
create table board_columns (
    id          bigserial primary key,
    project_id  bigint      not null references projects (id) on delete cascade,
    name        varchar(50) not null,
    position    varchar(64) collate "C" not null,
    wip_limit   int,
    done_column boolean     not null default false,
    version     bigint      not null default 0
);
create index idx_board_columns_project on board_columns (project_id, position);

create table tasks (
    id               bigserial primary key,
    project_id       bigint       not null references projects (id) on delete cascade,
    column_id        bigint       not null references board_columns (id),
    number           int          not null,
    title            varchar(200) not null,
    description      text,
    priority         varchar(10)  not null default 'MEDIUM',
    position         varchar(128) collate "C" not null,
    assignee_id      bigint       references users (id) on delete set null,
    reporter_id      bigint       not null references users (id),
    due_date         date,
    reminder_sent_at timestamptz,
    completed_at     timestamptz,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    version          bigint       not null default 0,
    unique (project_id, number)
);
create index idx_tasks_column_position on tasks (column_id, position);
create index idx_tasks_assignee on tasks (assignee_id);
create index idx_tasks_due_open on tasks (due_date) where completed_at is null and reminder_sent_at is null;
create index idx_tasks_title_trgm on tasks using gin (lower(title) gin_trgm_ops);

create table comments (
    id         bigserial primary key,
    task_id    bigint      not null references tasks (id) on delete cascade,
    author_id  bigint      not null references users (id) on delete cascade,
    body       text        not null,
    edited     boolean     not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_comments_task on comments (task_id, created_at);

-- Append-only audit trail.
create table activity_logs (
    id          bigserial primary key,
    project_id  bigint       references projects (id) on delete cascade,
    actor_id    bigint       references users (id) on delete set null,
    action      varchar(40)  not null,
    entity_type varchar(20)  not null,
    entity_id   bigint,
    summary     varchar(500) not null,
    created_at  timestamptz  not null default now()
);
create index idx_activity_project on activity_logs (project_id, created_at desc);
create index idx_activity_entity on activity_logs (entity_type, entity_id);

create table notifications (
    id         bigserial primary key,
    user_id    bigint       not null references users (id) on delete cascade,
    type       varchar(30)  not null,
    message    varchar(500) not null,
    link       varchar(255),
    read_at    timestamptz,
    created_at timestamptz  not null default now()
);
create index idx_notifications_user on notifications (user_id, created_at desc);
