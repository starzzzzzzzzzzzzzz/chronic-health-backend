-- Patient foundation; fictional seed data is initialized only in explicit local demo mode.
CREATE TABLE `patient` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `external_system` VARCHAR(64) COLLATE utf8mb4_bin NULL,
  `external_archive_id` VARCHAR(64) COLLATE utf8mb4_bin NULL,
  `name` VARCHAR(100) NOT NULL,
  `sex` VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
  `birth_date` DATE NULL,
  `team_id` VARCHAR(64) COLLATE utf8mb4_bin NULL,
  `status` VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
  `data_mode` VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
  `profile_revision` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  UNIQUE KEY `uk_0` (`org_id`,`external_system`,`external_archive_id`),
  KEY `idx_1` (`org_id`,`status`,`id`),
  KEY `idx_2` (`org_id`,`name`),
  CHECK (sex IN ('MALE','FEMALE','OTHER','UNKNOWN')),
  CHECK (status IN ('ACTIVE','INACTIVE')),
  CHECK (data_mode IN ('DEMO','REAL')),
  CHECK ((external_system IS NULL) = (external_archive_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `patient_access_grant` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `patient_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `capability` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  `valid_from` DATETIME(6) NOT NULL,
  `valid_until` DATETIME(6) NULL,
  `revoked_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  UNIQUE KEY `uk_0` (`org_id`,`patient_id`,`subject_id`,`capability`),
  KEY `idx_1` (`org_id`,`subject_id`,`capability`,`revoked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `health_fact` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `patient_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `axis_code` VARCHAR(8) COLLATE utf8mb4_bin NOT NULL,
  `fact_code` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `value_json` JSON NULL,
  `unit` VARCHAR(32) COLLATE utf8mb4_bin NULL,
  `source_type` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  `source_locator_json` JSON NULL,
  `observed_at` DATETIME(6) NULL,
  `confidence` DECIMAL(5,4) NULL,
  `confirmation_status` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  `confirmed_by` VARCHAR(64) COLLATE utf8mb4_bin NULL,
  `confirmed_at` DATETIME(6) NULL,
  `supersedes_fact_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `conflict_group_key` VARCHAR(64) COLLATE utf8mb4_bin NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  KEY `idx_0` (`org_id`,`patient_id`,`axis_code`,`fact_code`,`observed_at`),
  KEY `idx_1` (`org_id`,`patient_id`,`conflict_group_key`),
  UNIQUE KEY `uk_2` (`org_id`,`supersedes_fact_id`),
  CHECK (axis_code IN ('H','F','B','C','E','G')),
  CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  CHECK (supersedes_fact_id IS NULL OR supersedes_fact_id <> id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `health_input_snapshot` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `patient_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `stage` VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
  `profile_revision` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `captured_at` DATETIME(6) NOT NULL,
  `schema_version` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  `payload_json` JSON NOT NULL,
  `payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  KEY `idx_0` (`org_id`,`patient_id`,`stage`,`captured_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `patient_access_grant` ADD CONSTRAINT `fk_patient_access_grant_patient_id` FOREIGN KEY (`org_id`,`patient_id`) REFERENCES `patient` (`org_id`,`id`);
ALTER TABLE `health_fact` ADD CONSTRAINT `fk_health_fact_patient_id` FOREIGN KEY (`org_id`,`patient_id`) REFERENCES `patient` (`org_id`,`id`);
ALTER TABLE `health_fact` ADD CONSTRAINT `fk_health_fact_supersedes_fact_id` FOREIGN KEY (`org_id`,`supersedes_fact_id`) REFERENCES `health_fact` (`org_id`,`id`);
ALTER TABLE `health_input_snapshot` ADD CONSTRAINT `fk_health_input_snapshot_patient_id` FOREIGN KEY (`org_id`,`patient_id`) REFERENCES `patient` (`org_id`,`id`);