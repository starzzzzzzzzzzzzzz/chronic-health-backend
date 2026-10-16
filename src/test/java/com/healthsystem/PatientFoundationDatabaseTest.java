package com.healthsystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS", matches="true")
@Transactional
class PatientFoundationDatabaseTest {
    @Autowired JdbcTemplate db;
    @Test void migrationsCreateScopedTablesAndPreserveMetadata() {
        for (String table : new String[]{"patient","patient_access_grant","health_fact","health_input_snapshot","care_plan","care_plan_version","care_plan_module","plan_management_focus","care_plan_dimension","care_plan_item","plan_item_basis","audit_log"}) {
            assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Integer.class,table)).as(table).isEqualTo(1);
        }
        assertThat(db.queryForObject("SELECT metadata_value FROM app_metadata WHERE metadata_key='application'",String.class)).isEqualTo("chronic-health");
    }
    @Test void foreignKeysRejectCrossOrganizationReferences() {
        String patient=UUID.randomUUID().toString();
        insertPatient(patient);
        assertThatThrownBy(() -> db.update("INSERT INTO patient_access_grant(id,org_id,created_at,created_by,updated_at,row_version,patient_id,subject_id,capability,valid_from) VALUES(?, 'other-org', UTC_TIMESTAMP(6),'test',UTC_TIMESTAMP(6),0,?,'test','PATIENT_READ',UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),patient)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void oneFactCannotHaveTwoSuccessorsAndUtcHasNoDoubleShift() {
        String patient=UUID.randomUUID().toString(); insertPatient(patient);
        String original=UUID.randomUUID().toString();
        insertFact(original,patient,null);
        insertFact(UUID.randomUUID().toString(),patient,original);
        assertThatThrownBy(() -> insertFact(UUID.randomUUID().toString(),patient,original)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(db.queryForObject("SELECT created_at FROM patient WHERE id=?",LocalDateTime.class,patient)).isEqualTo(LocalDateTime.of(2026,10,16,16,30));
    }
    void insertPatient(String id) {
        db.update("INSERT INTO patient(id,org_id,created_at,created_by,updated_at,row_version,name,sex,status,data_mode,profile_revision) VALUES(?,'test-org','2026-10-16 16:30:00','test','2026-10-16 16:30:00',0,'虚构患者','UNKNOWN','ACTIVE','DEMO',0)",id);
    }
    void insertFact(String id,String patient,String previous) {
        db.update("INSERT INTO health_fact(id,org_id,created_at,created_by,patient_id,axis_code,fact_code,source_type,confirmation_status,supersedes_fact_id) VALUES(?,'test-org',UTC_TIMESTAMP(6),'test',?,'B','sleep','DEMO','REPORTED',?)",id,patient,previous);
    }
}
