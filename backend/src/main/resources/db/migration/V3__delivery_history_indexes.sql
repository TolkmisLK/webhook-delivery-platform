create index idx_delivery_job_history
    on delivery_job (created_at desc, id desc);

create index idx_delivery_job_history_status
    on delivery_job (status, created_at desc, id desc);

create index idx_delivery_job_history_endpoint
    on delivery_job (endpoint_id, created_at desc, id desc);

create index idx_delivery_job_history_endpoint_status
    on delivery_job (endpoint_id, status, created_at desc, id desc);
