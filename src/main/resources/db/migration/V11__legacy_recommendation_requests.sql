-- Retrying an old synchronous generation request must not create another paid job.
CREATE TABLE legacy_recommendation_request (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    request_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    generation_job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_legacy_recommendation_request (org_id, created_by, request_key),
    FOREIGN KEY (org_id, generation_job_id) REFERENCES plan_generation_job (org_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
