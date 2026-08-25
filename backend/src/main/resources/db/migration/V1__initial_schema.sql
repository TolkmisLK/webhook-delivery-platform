create table webhook_endpoint (
    id uuid primary key,
    name varchar(120) not null,
    url varchar(2048) not null,
    encrypted_secret varchar(1024) not null,
    active boolean not null,
    created_at timestamptz not null,
    version bigint not null default 0
);

create table webhook_event (
    id uuid primary key,
    endpoint_id uuid not null references webhook_endpoint(id),
    event_type varchar(160) not null,
    body text not null,
    idempotency_key varchar(200) not null,
    created_at timestamptz not null,
    constraint uk_event_endpoint_idempotency unique (endpoint_id, idempotency_key)
);

create table delivery_job (
    id uuid primary key,
    event_id uuid not null unique references webhook_event(id),
    endpoint_id uuid not null references webhook_endpoint(id),
    status varchar(32) not null,
    attempt_count integer not null,
    max_attempts integer not null,
    next_attempt_at timestamptz not null,
    locked_at timestamptz,
    locked_by varchar(160),
    last_status_code integer,
    last_error varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);

create index idx_delivery_job_claim
    on delivery_job (next_attempt_at, created_at)
    where status in ('PENDING', 'RETRY_SCHEDULED');

create index idx_delivery_job_stale
    on delivery_job (locked_at)
    where status = 'PROCESSING';

create table delivery_attempt (
    id uuid primary key,
    job_id uuid not null references delivery_job(id),
    attempt_number integer not null,
    outcome varchar(32) not null,
    status_code integer,
    error_message varchar(1000),
    response_excerpt varchar(4096),
    duration_ms bigint not null,
    started_at timestamptz not null,
    finished_at timestamptz not null,
    constraint uk_delivery_attempt_number unique (job_id, attempt_number)
);

create index idx_delivery_attempt_job on delivery_attempt (job_id, attempt_number desc);
