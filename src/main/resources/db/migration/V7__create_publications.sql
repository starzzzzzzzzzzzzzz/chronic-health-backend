CREATE TABLE publication_preview (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 task_bundle_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 bundle_revision BIGINT UNSIGNED NOT NULL, intent_json JSON NOT NULL, manifest_json JSON NOT NULL, preview_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 UNIQUE KEY uk_pub_preview_scope(org_id,id),
 FOREIGN KEY(org_id,task_bundle_id) REFERENCES task_bundle(org_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE publication (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 patient_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 task_bundle_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 review_submission_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 schedule_preview_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 publication_preview_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, effective_at DATETIME(6) NOT NULL,
 status VARCHAR(16) COLLATE utf8mb4_bin NOT NULL, notification_status VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
 UNIQUE KEY uk_publication_scope(org_id,id), UNIQUE KEY uk_publication_bundle(org_id,task_bundle_id),
 KEY idx_publication_patient(org_id,patient_id,effective_at),
 FOREIGN KEY(org_id,patient_id) REFERENCES patient(org_id,id),
 FOREIGN KEY(org_id,task_bundle_id) REFERENCES task_bundle(org_id,id),
 FOREIGN KEY(org_id,review_submission_id) REFERENCES review_submission(org_id,id),
 FOREIGN KEY(org_id,schedule_preview_id) REFERENCES schedule_preview(org_id,id),
 FOREIGN KEY(org_id,publication_preview_id) REFERENCES publication_preview(org_id,id),
 CHECK(status='PUBLISHED'), CHECK(notification_status='NOT_CONFIGURED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE publication_request (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 publication_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 UNIQUE KEY uk_pub_request_scope(org_id,id), UNIQUE KEY uk_pub_request_key(org_id,idempotency_key),
 FOREIGN KEY(org_id,publication_id) REFERENCES publication(org_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE execution_task (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 patient_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 publication_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 task_bundle_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 group_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 variant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 occurrence_key VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, dimension_code VARCHAR(24) COLLATE utf8mb4_bin NOT NULL,
 execution_date DATE NOT NULL, zone_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, slot_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 planned_at DATETIME(6) NOT NULL, opens_at DATETIME(6) NOT NULL, closes_at DATETIME(6) NOT NULL, normal_cutoff_at DATETIME(6) NOT NULL, makeup_cutoff_at DATETIME(6) NOT NULL,
 content_snapshot_json JSON NOT NULL, task_class VARCHAR(16) COLLATE utf8mb4_bin NOT NULL, priority_code VARCHAR(8) COLLATE utf8mb4_bin NOT NULL, safety_focus BOOLEAN NOT NULL,
 cancelled_at DATETIME(6) NULL, replaced_by_publication_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 UNIQUE KEY uk_execution_scope(org_id,id), UNIQUE KEY uk_execution_occurrence(org_id,publication_id,occurrence_key),
 KEY idx_execution_patient(org_id,patient_id,execution_date,planned_at),
 FOREIGN KEY(org_id,patient_id) REFERENCES patient(org_id,id),
 FOREIGN KEY(org_id,publication_id) REFERENCES publication(org_id,id),
 FOREIGN KEY(org_id,task_bundle_id) REFERENCES task_bundle(org_id,id),
 FOREIGN KEY(org_id,group_id) REFERENCES task_group(org_id,id),
 FOREIGN KEY(org_id,variant_id) REFERENCES task_group_variant(org_id,id),
 FOREIGN KEY(org_id,replaced_by_publication_id) REFERENCES publication(org_id,id),
 CHECK(opens_at<closes_at), CHECK(normal_cutoff_at>=closes_at), CHECK(task_class IN ('REQUIRED','OPTIONAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE outbox_event (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 publication_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_type VARCHAR(32) COLLATE utf8mb4_bin NOT NULL, status VARCHAR(32) COLLATE utf8mb4_bin NOT NULL, payload_json JSON NOT NULL,
 UNIQUE KEY uk_outbox_scope(org_id,id), UNIQUE KEY uk_outbox_publication(org_id,publication_id,event_type),
 FOREIGN KEY(org_id,publication_id) REFERENCES publication(org_id,id), CHECK(status='NOT_CONFIGURED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
