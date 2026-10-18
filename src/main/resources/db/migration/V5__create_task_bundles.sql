CREATE TABLE task_template (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 template_code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, dimension_code VARCHAR(24) COLLATE utf8mb4_bin NOT NULL,
 version_no INT NOT NULL, review_status VARCHAR(32) COLLATE utf8mb4_bin NOT NULL, data_mode VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
 content_json JSON NOT NULL, content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 UNIQUE KEY uk_template_scope(org_id,id), UNIQUE KEY uk_template_version(org_id,template_code,version_no),
 CHECK(dimension_code IN ('medication','diet','exercise','routine','monitoring','tcm','other')),
 CHECK(data_mode IN ('DEMO','REAL')), CHECK(review_status IN ('DEMO_ONLY','MEDICALLY_APPROVED','DISABLED')), CHECK(version_no>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_bundle (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 patient_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, plan_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_review_submission_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, source_review_decision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_revision BIGINT UNSIGNED NOT NULL, source_plan_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_review_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, input_snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_snapshot_json JSON NOT NULL, next_cycle_reference_json JSON NOT NULL,
 based_on_bundle_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 version_no INT NOT NULL, row_version BIGINT UNSIGNED NOT NULL DEFAULT 0, content_revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
 status VARCHAR(16) COLLATE utf8mb4_bin NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL, zone_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 generation_mode VARCHAR(32) COLLATE utf8mb4_bin NOT NULL, content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 latest_preview_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_bundle_scope(org_id,id), UNIQUE KEY uk_bundle_version(org_id,plan_version_id,version_no),
 KEY idx_bundle_patient(org_id,patient_id,status),
 CONSTRAINT fk_bundle_patient FOREIGN KEY(org_id,patient_id) REFERENCES patient(org_id,id),
 CONSTRAINT fk_bundle_plan FOREIGN KEY(org_id,plan_version_id) REFERENCES care_plan_version(org_id,id),
 CONSTRAINT fk_bundle_review FOREIGN KEY(org_id,source_review_submission_id) REFERENCES review_submission(org_id,id),
 CONSTRAINT fk_bundle_decision FOREIGN KEY(org_id,source_review_decision_id) REFERENCES review_decision(org_id,id),
 CONSTRAINT fk_bundle_input FOREIGN KEY(org_id,input_snapshot_id) REFERENCES health_input_snapshot(org_id,id),
 CONSTRAINT fk_bundle_base FOREIGN KEY(org_id,based_on_bundle_id) REFERENCES task_bundle(org_id,id),
 CHECK(status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED')), CHECK(start_date<=end_date), CHECK(version_no>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_group (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 task_bundle_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, dimension_code VARCHAR(24) COLLATE utf8mb4_bin NOT NULL,
 actor_type VARCHAR(24) COLLATE utf8mb4_bin NOT NULL, task_class VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
 priority_code VARCHAR(8) COLLATE utf8mb4_bin NOT NULL, safety_focus BOOLEAN NOT NULL, evaluation_mode VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
 title VARCHAR(200) NOT NULL, content_json JSON NOT NULL, schedule_rule_json JSON NULL, sort_order INT NOT NULL,
 UNIQUE KEY uk_group_scope(org_id,id), KEY idx_group_bundle(org_id,task_bundle_id,sort_order),
 CONSTRAINT fk_group_bundle FOREIGN KEY(org_id,task_bundle_id) REFERENCES task_bundle(org_id,id),
 CHECK(dimension_code IN ('medication','diet','exercise','routine','monitoring','tcm','other')),
 CHECK(actor_type IN ('PATIENT','CAREGIVER','PROFESSIONAL','SHARED')), CHECK(task_class IN ('REQUIRED','OPTIONAL')),
 CHECK(priority_code IN ('L0','L1','L2','L3','L4')), CHECK(evaluation_mode='COMPLETION_ONLY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_group_variant (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 task_group_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, variant_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 title VARCHAR(200) NOT NULL, content TEXT NOT NULL, parameters_json JSON NOT NULL, sort_order INT NOT NULL,
 UNIQUE KEY uk_variant_scope(org_id,id), UNIQUE KEY uk_variant_key(org_id,task_group_id,variant_key),
 CONSTRAINT fk_variant_group FOREIGN KEY(org_id,task_group_id) REFERENCES task_group(org_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_group_source (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 task_group_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, plan_item_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 fact_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, module_code VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
 UNIQUE KEY uk_source_scope(org_id,id), UNIQUE KEY uk_group_basis(org_id,task_group_id,plan_item_id,fact_id,module_code),
 CONSTRAINT fk_source_group FOREIGN KEY(org_id,task_group_id) REFERENCES task_group(org_id,id),
 CONSTRAINT fk_source_item FOREIGN KEY(org_id,plan_item_id) REFERENCES care_plan_item(org_id,id),
 CONSTRAINT fk_source_fact FOREIGN KEY(org_id,fact_id) REFERENCES health_fact(org_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_preview (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, created_at DATETIME(6) NOT NULL, created_by VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 task_bundle_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, bundle_revision BIGINT UNSIGNED NOT NULL,
 content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, preview_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 rule_version VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, occurrences_json JSON NOT NULL, issues_json JSON NOT NULL,
 UNIQUE KEY uk_preview_scope(org_id,id), KEY idx_preview_bundle(org_id,task_bundle_id,bundle_revision),
 CONSTRAINT fk_preview_bundle FOREIGN KEY(org_id,task_bundle_id) REFERENCES task_bundle(org_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE task_bundle ADD CONSTRAINT fk_bundle_preview FOREIGN KEY(org_id,latest_preview_id) REFERENCES schedule_preview(org_id,id);
