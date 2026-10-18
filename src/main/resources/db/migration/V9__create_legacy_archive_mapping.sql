-- Local opaque aliases for the legacy frontend; not IDs from the reference server.
CREATE TABLE legacy_archive_mapping (
    archive_id BIGINT NOT NULL AUTO_INCREMENT,
    org_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    patient_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (archive_id),
    UNIQUE KEY uk_legacy_archive_patient (org_id, patient_id),
    CONSTRAINT fk_legacy_archive_patient FOREIGN KEY (org_id, patient_id)
        REFERENCES patient (org_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO legacy_archive_mapping (org_id, patient_id, created_at)
SELECT org_id, id, UTC_TIMESTAMP(6) FROM patient ORDER BY org_id, id;
