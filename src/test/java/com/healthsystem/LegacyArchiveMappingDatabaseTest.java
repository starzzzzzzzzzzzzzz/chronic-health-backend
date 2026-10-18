package com.healthsystem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.healthsystem.service.impl.DemoDataInitializer;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class LegacyArchiveMappingDatabaseTest extends DatabaseApiTestSupport {
    @Autowired DemoDataInitializer initializer;
    @Autowired com.healthsystem.service.LegacyArchiveMappingService mappings;
    static final String DEMO_ID="00000000-0000-4000-8000-000000000001";
    @Test void migrationProvidesPersistentArchiveMappings() {
        assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='legacy_archive_mapping'", Integer.class)).isEqualTo(1);
    }

    @Test void demoInitializationRestoresMissingAliasWithoutRestoringRevokedGrants() throws Exception {
        db.update("DELETE FROM legacy_archive_mapping WHERE patient_id=?", DEMO_ID);
        db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=?",DEMO_ID);
        initializer.run(null);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM legacy_archive_mapping WHERE patient_id=?",Integer.class,DEMO_ID)).isEqualTo(1);
        long first=db.queryForObject("SELECT archive_id FROM legacy_archive_mapping WHERE patient_id=?",Long.class,DEMO_ID);
        initializer.run(null);
        assertThat(db.queryForObject("SELECT archive_id FROM legacy_archive_mapping WHERE patient_id=?",Long.class,DEMO_ID)).isEqualTo(first);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM patient_access_grant WHERE patient_id=? AND revoked_at IS NULL",Integer.class,DEMO_ID)).isZero();
    }

    @Test void migrationBackfillsExistingPatientsWithoutChangingExternalIdentifiers() throws Exception {
        String id=patient("demo-org","DEMO","migration fixture",false);
        db.update("UPDATE patient SET external_system='upstream', external_archive_id='external-55' WHERE id=?",id);
        // Exercise the migration's real INSERT SELECT against pre-existing fixtures.
        db.update("DELETE FROM legacy_archive_mapping");
        String sql=new ClassPathResource("db/migration/V9__create_legacy_archive_mapping.sql").getContentAsString(StandardCharsets.UTF_8);
        db.update(sql.substring(sql.indexOf("INSERT INTO")));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM patient p LEFT JOIN legacy_archive_mapping m ON m.org_id=p.org_id AND m.patient_id=p.id WHERE m.archive_id IS NULL",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT external_archive_id FROM patient WHERE id=?",String.class,id)).isEqualTo("external-55");
    }

    @Test void mappingConstraintsRejectWrongOrganizationAndDuplicatePatient() {
        String id=patient("demo-org","DEMO","constraint fixture",false);
        assertThatThrownBy(()->db.update("INSERT INTO legacy_archive_mapping(org_id,patient_id,created_at) VALUES('wrong-org',?,UTC_TIMESTAMP(6))",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        db.update("INSERT INTO legacy_archive_mapping(org_id,patient_id,created_at) VALUES('demo-org',?,UTC_TIMESTAMP(6))",id);
        assertThatThrownBy(()->db.update("INSERT INTO legacy_archive_mapping(org_id,patient_id,created_at) VALUES('demo-org',?,UTC_TIMESTAMP(6))",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void patientAndAliasShareTheCallersRollback() {
        String id=patient("demo-org","DEMO","rollback fixture",false);
        Long alias=mappings.ensureForPatient("demo-org",id);
        assertThat(alias).isPositive();
        assertThat(mappings.ensureForPatient("demo-org",id)).isEqualTo(alias);
        org.springframework.test.context.transaction.TestTransaction.flagForRollback();
        org.springframework.test.context.transaction.TestTransaction.end();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM legacy_archive_mapping WHERE patient_id=?",Integer.class,id)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM patient WHERE id=?",Integer.class,id)).isZero();
        assertThatThrownBy(()->mappings.ensureForPatient("demo-org",id))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test void invalidStoredAliasCannotBeReturnedAsSuccessfulInitialization() {
        String id=patient("demo-org","DEMO","invalid alias fixture",false);
        db.update("INSERT INTO legacy_archive_mapping(archive_id,org_id,patient_id,created_at) VALUES(-1,'demo-org',?,UTC_TIMESTAMP(6))",id);
        assertThatThrownBy(()->mappings.ensureForPatient("demo-org",id))
                .isInstanceOf(com.healthsystem.exception.BusinessException.class)
                .extracting(e->((com.healthsystem.exception.BusinessException)e).status()).isEqualTo(409);
    }
}
