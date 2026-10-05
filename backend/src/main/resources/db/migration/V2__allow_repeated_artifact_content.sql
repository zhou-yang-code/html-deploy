drop index uq_artifact_project_sha256;

create index idx_artifact_project_sha256
    on artifact (project_id, sha256);
