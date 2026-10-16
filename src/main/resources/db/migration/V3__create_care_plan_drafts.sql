-- Care plan drafts; create all tables before adding cyclic foreign keys.
CREATE TABLE `care_plan` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `patient_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `plan_no` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `current_version_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `status` VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
  `closed_reason` VARCHAR(500) COLLATE utf8mb4_bin NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  UNIQUE KEY `uk_0` (`org_id`,`plan_no`),
  KEY `idx_1` (`org_id`,`patient_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `care_plan_version` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `plan_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version_no` INT UNSIGNED NOT NULL,
  `based_on_version_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `title` VARCHAR(200) NOT NULL,
  `start_date` DATE NOT NULL,
  `end_date` DATE NOT NULL,
  `zone_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `primary_goal` VARCHAR(1000) COLLATE utf8mb4_bin NULL,
  `goal_basis_json` JSON NULL,
  `supplemental_requirements` TEXT NULL,
  `input_snapshot_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `status` VARCHAR(24) COLLATE utf8mb4_bin NOT NULL,
  `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `change_reason` VARCHAR(1000) NULL,
  `content_revision` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  UNIQUE KEY `uk_0` (`org_id`,`plan_id`,`version_no`),
  KEY `idx_1` (`org_id`,`status`,`updated_at`),
  CHECK (start_date <= end_date),
  CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','SUPERSEDED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `care_plan_module` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `plan_version_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `module_code` VARCHAR(8) COLLATE utf8mb4_bin NOT NULL,
  `selection_reason` TEXT NOT NULL,
  `basis_json` JSON NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  UNIQUE KEY `uk_0` (`org_id`,`plan_version_id`,`module_code`),
  CHECK (module_code IN ('B0','L','R','C','F','A'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plan_management_focus` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `plan_version_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `diagnosis_fact_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `primary_subtype_code` VARCHAR(64) COLLATE utf8mb4_bin NULL,
  `secondary_subtypes_json` JSON NULL,
  `priority_code` VARCHAR(32) COLLATE utf8mb4_bin NULL,
  `dimension_weights_json` JSON NULL,
  `contribution_bps` INT UNSIGNED NULL,
  `adjustment_reason` TEXT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  UNIQUE KEY `uk_0` (`org_id`,`plan_version_id`,`diagnosis_fact_id`),
  CHECK (contribution_bps IS NULL OR contribution_bps BETWEEN 0 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `care_plan_dimension` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `plan_version_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `dimension_code` VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
  `decision` VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
  `decision_reason` TEXT NULL,
  `sort_order` INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  UNIQUE KEY `uk_0` (`org_id`,`plan_version_id`,`dimension_code`),
  CHECK (dimension_code IN ('medication','diet','exercise','routine','monitoring','tcm','other')),
  CHECK (decision IN ('ENABLED','CLOSED','PENDING')),
  CHECK (decision='ENABLED' OR decision_reason IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `care_plan_item` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `plan_version_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `dimension_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `content` TEXT NOT NULL,
  `execution_goal` BOOLEAN NOT NULL,
  `actor_type` VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
  `contraindications_json` JSON NULL,
  `professional_parameters_json` JSON NULL,
  `references_json` JSON NULL,
  `is_selected` BOOLEAN NOT NULL,
  `sort_order` INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  KEY `idx_0` (`org_id`,`plan_version_id`,`dimension_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plan_item_basis` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `plan_item_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `module_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `fact_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `basis_note` VARCHAR(1000) COLLATE utf8mb4_bin NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  UNIQUE KEY `uk_0` (`org_id`,`plan_item_id`,`module_id`,`fact_id`),
  KEY `idx_1` (`org_id`,`fact_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_log` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `org_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `actor_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `action` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `target_type` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  `target_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `before_revision` BIGINT UNSIGNED NULL,
  `after_revision` BIGINT UNSIGNED NULL,
  `request_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  `change_summary_json` JSON NOT NULL,
  `occurred_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`org_id`,`id`),
  KEY `idx_0` (`org_id`,`target_type`,`target_id`,`occurred_at`),
  KEY `idx_1` (`org_id`,`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `care_plan` ADD CONSTRAINT `fk_care_plan_patient_id` FOREIGN KEY (`org_id`,`patient_id`) REFERENCES `patient` (`org_id`,`id`);
ALTER TABLE `care_plan` ADD CONSTRAINT `fk_care_plan_current_version_id` FOREIGN KEY (`org_id`,`current_version_id`) REFERENCES `care_plan_version` (`org_id`,`id`);
ALTER TABLE `care_plan_version` ADD CONSTRAINT `fk_care_plan_version_plan_id` FOREIGN KEY (`org_id`,`plan_id`) REFERENCES `care_plan` (`org_id`,`id`);
ALTER TABLE `care_plan_version` ADD CONSTRAINT `fk_care_plan_version_based_on_version_id` FOREIGN KEY (`org_id`,`based_on_version_id`) REFERENCES `care_plan_version` (`org_id`,`id`);
ALTER TABLE `care_plan_version` ADD CONSTRAINT `fk_care_plan_version_input_snapshot_id` FOREIGN KEY (`org_id`,`input_snapshot_id`) REFERENCES `health_input_snapshot` (`org_id`,`id`);
ALTER TABLE `care_plan_module` ADD CONSTRAINT `fk_care_plan_module_plan_version_id` FOREIGN KEY (`org_id`,`plan_version_id`) REFERENCES `care_plan_version` (`org_id`,`id`);
ALTER TABLE `plan_management_focus` ADD CONSTRAINT `fk_plan_management_focus_plan_version_id` FOREIGN KEY (`org_id`,`plan_version_id`) REFERENCES `care_plan_version` (`org_id`,`id`);
ALTER TABLE `plan_management_focus` ADD CONSTRAINT `fk_plan_management_focus_diagnosis_fact_id` FOREIGN KEY (`org_id`,`diagnosis_fact_id`) REFERENCES `health_fact` (`org_id`,`id`);
ALTER TABLE `care_plan_dimension` ADD CONSTRAINT `fk_care_plan_dimension_plan_version_id` FOREIGN KEY (`org_id`,`plan_version_id`) REFERENCES `care_plan_version` (`org_id`,`id`);
ALTER TABLE `care_plan_item` ADD CONSTRAINT `fk_care_plan_item_plan_version_id` FOREIGN KEY (`org_id`,`plan_version_id`) REFERENCES `care_plan_version` (`org_id`,`id`);
ALTER TABLE `care_plan_item` ADD CONSTRAINT `fk_care_plan_item_dimension_id` FOREIGN KEY (`org_id`,`dimension_id`) REFERENCES `care_plan_dimension` (`org_id`,`id`);
ALTER TABLE `plan_item_basis` ADD CONSTRAINT `fk_plan_item_basis_plan_item_id` FOREIGN KEY (`org_id`,`plan_item_id`) REFERENCES `care_plan_item` (`org_id`,`id`);
ALTER TABLE `plan_item_basis` ADD CONSTRAINT `fk_plan_item_basis_module_id` FOREIGN KEY (`org_id`,`module_id`) REFERENCES `care_plan_module` (`org_id`,`id`);
ALTER TABLE `plan_item_basis` ADD CONSTRAINT `fk_plan_item_basis_fact_id` FOREIGN KEY (`org_id`,`fact_id`) REFERENCES `health_fact` (`org_id`,`id`);