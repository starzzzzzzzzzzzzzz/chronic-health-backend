CREATE TABLE task_feedback (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 execution_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 previous_feedback_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 version_no INT NOT NULL, idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, body_json JSON NOT NULL, assessment_json JSON NOT NULL,
 received_at DATETIME(6) NOT NULL, submitted_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 UNIQUE KEY uk_feedback_scope(org_id,id), UNIQUE KEY uk_feedback_version(org_id,execution_task_id,version_no), UNIQUE KEY uk_feedback_request(org_id,idempotency_key),
 FOREIGN KEY(org_id,execution_task_id) REFERENCES execution_task(org_id,id), CHECK(version_no>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE task_result (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 execution_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 current_feedback_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, row_version BIGINT UNSIGNED NOT NULL,
 assessment_json JSON NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_result_scope(org_id,id), UNIQUE KEY uk_result_task(org_id,execution_task_id),
 FOREIGN KEY(org_id,execution_task_id) REFERENCES execution_task(org_id,id),
 FOREIGN KEY(org_id,current_feedback_id) REFERENCES task_feedback(org_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE task_safety_event (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 execution_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 feedback_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, status VARCHAR(16) COLLATE utf8mb4_bin NOT NULL, signal_json JSON NOT NULL,
 UNIQUE KEY uk_safety_scope(org_id,id), UNIQUE KEY uk_safety_feedback(org_id,feedback_id), KEY idx_safety_task(org_id,execution_task_id,status),
 FOREIGN KEY(org_id,execution_task_id) REFERENCES execution_task(org_id,id),
 FOREIGN KEY(org_id,feedback_id) REFERENCES task_feedback(org_id,id), CHECK(status='OPEN')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
