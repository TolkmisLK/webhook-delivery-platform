alter table delivery_job
    add column target_url varchar(2048),
    add column encrypted_secret varchar(1024);

update delivery_job job
set target_url = endpoint.url,
    encrypted_secret = endpoint.encrypted_secret
from webhook_endpoint endpoint
where endpoint.id = job.endpoint_id;

alter table delivery_job
    alter column target_url set not null,
    alter column encrypted_secret set not null;
