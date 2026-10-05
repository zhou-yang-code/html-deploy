create table user_account (
    id uuid primary key,
    email varchar(320) not null unique,
    password_hash varchar(255) not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null
);

create table tenant (
    id uuid primary key,
    name varchar(120) not null,
    slug varchar(40) not null unique,
    created_at timestamp with time zone not null
);

create table tenant_member (
    tenant_id uuid not null,
    user_id uuid not null,
    role varchar(32) not null,
    created_at timestamp with time zone not null,
    primary key (tenant_id, user_id),
    constraint fk_tenant_member_tenant foreign key (tenant_id) references tenant (id),
    constraint fk_tenant_member_user foreign key (user_id) references user_account (id)
);

create table project (
    id uuid primary key,
    tenant_id uuid not null,
    name varchar(120) not null,
    slug varchar(50) not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_project_tenant_slug unique (tenant_id, slug),
    constraint fk_project_tenant foreign key (tenant_id) references tenant (id)
);

create table artifact (
    id uuid primary key,
    project_id uuid not null,
    original_filename varchar(255) not null,
    object_key varchar(512) not null,
    status varchar(32) not null,
    size_bytes bigint not null,
    sha256 varchar(64),
    manifest_json text,
    error_code varchar(120),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_artifact_project foreign key (project_id) references project (id)
);

create unique index uq_artifact_project_sha256
    on artifact (project_id, sha256);

create table deployment (
    id uuid primary key,
    project_id uuid not null,
    environment varchar(64) not null,
    artifact_id uuid not null,
    version bigint not null,
    status varchar(32) not null,
    release_path varchar(1024),
    error_code varchar(120),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    finished_at timestamp with time zone,
    constraint uq_deployment_project_version unique (project_id, version),
    constraint fk_deployment_project foreign key (project_id) references project (id),
    constraint fk_deployment_artifact foreign key (artifact_id) references artifact (id)
);

create index idx_deployment_project_created
    on deployment (project_id, created_at desc);

create table release_channel (
    id uuid primary key,
    project_id uuid not null,
    environment varchar(64) not null,
    active_deployment_id uuid,
    updated_at timestamp with time zone not null,
    constraint uq_release_channel_project_environment unique (project_id, environment),
    constraint fk_release_channel_project foreign key (project_id) references project (id),
    constraint fk_release_channel_deployment foreign key (active_deployment_id) references deployment (id)
);

create table outbox_event (
    id uuid primary key,
    event_type varchar(120) not null,
    aggregate_type varchar(120) not null,
    aggregate_id uuid not null,
    tenant_id uuid,
    project_id uuid,
    payload text not null,
    status varchar(32) not null,
    attempts int not null,
    next_attempt_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    published_at timestamp with time zone,
    last_error text
);

create index idx_outbox_pending
    on outbox_event (status, next_attempt_at, created_at);
