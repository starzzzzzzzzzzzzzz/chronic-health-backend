package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class PlanReviewSchemaDatabaseTest extends ReviewApiTestSupport {
    String insertSubmission(String version,String org) {
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO review_submission(id,org_id,created_at,created_by,updated_at,row_version,stage,plan_version_id,target_revision,content_hash,content_snapshot_json,status,submitted_by,submitted_at) VALUES(?,?,UTC_TIMESTAMP(6),'test',UTC_TIMESTAMP(6),0,'PLAN',?,1,REPEAT('a',64),'{}','PENDING','test',UTC_TIMESTAMP(6))",id,org,version);return id;
    }
    @Test void preventsDuplicatePendingSubmissionAndCrossOrganizationTarget() throws Exception {
        var f=draft("routine",false);insertSubmission(f.version(),"demo-org");
        assertThatThrownBy(()->insertSubmission(f.version(),"demo-org")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->insertSubmission(f.version(),"other-org")).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void onlyOneDecisionCanExistForEachSubmission() throws Exception {
        var f=draft("routine",false);String s=insertSubmission(f.version(),"demo-org");
        String sql="INSERT INTO review_decision(id,org_id,created_at,created_by,submission_id,decision,reviewer_id,reviewed_at) VALUES(?,'demo-org',UTC_TIMESTAMP(6),'test',?,'APPROVE','test',UTC_TIMESTAMP(6))";
        db.update(sql,UUID.randomUUID().toString(),s);
        assertThatThrownBy(()->db.update(sql,UUID.randomUUID().toString(),s)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
