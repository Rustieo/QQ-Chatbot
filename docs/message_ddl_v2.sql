-- PostgreSQL DDL for message persistence
-- Based on docs/message.md
--
-- Notes:
-- - meta uses jsonb for flexible metadata
-- - create_time uses timestamptz, default now()
-- - role is constrained via CHECK to 'assistant' | 'user'
--
-- Execute in psql (or any SQL client) connected to your database.

-- =========================
-- 1) Group chat messages
-- =========================
create table if not exists group_chat_message (
    id            bigserial primary key,
    group_id      bigint not null,
    group_member  bigint not null,
    role          varchar(16) not null,
    message       text not null,
    create_time   timestamptz not null default now(),
    constraint ck_group_chat_message_role check (role in ('assistant', 'user'))
);

create index if not exists idx_group_chat_message_group_time
    on group_chat_message (group_id, create_time desc);

create index if not exists idx_group_chat_message_member_time
    on group_chat_message (group_member, create_time desc);


-- =========================
-- 2) Private chat messages
-- =========================
create table if not exists private_chat_message (
    id           bigserial primary key,
    user_id      bigint not null,
    role         varchar(16) not null,
    message      text not null,
    create_time  timestamptz not null default now(),
    constraint ck_private_chat_message_role check (role in ('assistant', 'user'))
);

create index if not exists idx_private_chat_message_user_time
    on private_chat_message (user_id, create_time desc);

