package com.healthsystem;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Transactional
class WorkflowQueryApiTest extends ReviewApiTestSupport {
    @org.springframework.beans.factory.annotation.Autowired com.healthsystem.service.impl.WorkflowQueryService queries;
    @Test void listsOnlyAccessibleCurrentVersionsAndValidatesPagination() throws Exception {
        var f=draft("routine",false);
        var rows=getJson("/care-plans?patientId="+f.patient()+"&size=1",TOKEN,200);
        assertThat(rows.path("total").asLong()).isEqualTo(1);
        assertThat(rows.path("items").get(0).path("versionId").asText()).isEqualTo(f.version());
        assertThat(rows.path("items").get(0).path("rowVersion").isString()).isTrue();
        assertThat(getJson("/care-plans?patientId="+f.patient()+"&page=2&size=1",TOKEN,200).path("items").isEmpty()).isTrue();
        getJson("/care-plans?page=0",TOKEN,400);getJson("/care-plans?size=101",TOKEN,400);getJson("/care-plans?status=BOGUS",TOKEN,400);
        db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=? AND subject_id='demo-clinician' AND capability='PATIENT_READ'",f.patient());
        assertThat(getJson("/care-plans?patientId="+f.patient(),TOKEN,200).path("total").asLong()).isZero();
    }
    @Test void pendingQueueRequiresIndependentReviewerAndActiveGrant() throws Exception {
        var f=draft("routine",false);var s=submit(f,"1");String id=s.path("id").asText();
        assertThat(getJson("/review-submissions?scope=pending&stage=PLAN",REVIEWER,200).toString()).contains(id);
        grant(f.patient(),"demo-clinician","PLAN_REVIEW");
        assertThat(getJson("/review-submissions",TOKEN,200).toString()).doesNotContain(id);
        assertThat(getJson("/review-submissions?scope=mine",TOKEN,200).toString()).contains(id);
        db.update("UPDATE patient_access_grant SET valid_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE patient_id=? AND subject_id='demo-reviewer' AND capability='PLAN_REVIEW'",f.patient());
        assertThat(getJson("/review-submissions",REVIEWER,200).toString()).doesNotContain(id);
        getJson("/review-submissions?scope=all",TOKEN,400);getJson("/review-submissions?stage=BOGUS",TOKEN,400);
    }
    @Test void workflowRequiresTaskReadAndKeepsVersionIdentity() throws Exception {
        var f=draft("routine",false);getJson("/plan-versions/"+f.version()+"/workflow",TOKEN,403);
        grant(f.patient(),"demo-clinician","TASK_READ");
        var w=getJson("/plan-versions/"+f.version()+"/workflow",TOKEN,200);
        assertThat(w.path("currentVersionId").asText()).isEqualTo(f.version());
        assertThat(w.path("taskBundles").isEmpty()).isTrue();assertThat(w.path("publications").isEmpty()).isTrue();
        var s=submit(f,"1");postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),201);
        var n=postJson("/care-plans/"+f.plan()+"/versions",TOKEN,Map.of("sourceVersionId",f.version(),"expectedRevision","3","changeReason","测试新版本发现"),201);
        w=getJson("/plan-versions/"+f.version()+"/workflow",TOKEN,200);
        assertThat(w.path("versionId").asText()).isEqualTo(f.version());assertThat(w.path("currentVersionId").asText()).isEqualTo(n.path("versionId").asText());
        assertThat(w.path("versions").size()).isEqualTo(2);assertThat(w.path("reviewSubmissions").size()).isEqualTo(1);
        assertThat(getJson("/care-plans?patientId="+f.patient(),TOKEN,200).path("items").get(0).path("versionId").asText()).isEqualTo(n.path("versionId").asText());
    }
    @Test void wrongModeAndOrgNeverLeakIntoCountsOrDetails() throws Exception {
        var f=draft("routine",false);grant(f.patient(),"demo-clinician","TASK_READ");
        db.update("UPDATE patient SET data_mode='REAL' WHERE id=?",f.patient());
        assertThat(getJson("/care-plans?patientId="+f.patient(),TOKEN,200).path("total").asLong()).isZero();
        getJson("/plan-versions/"+f.version()+"/workflow",TOKEN,404);
        db.update("UPDATE patient SET data_mode='DEMO' WHERE id=?",f.patient());
        assertThat(queries.plans(new com.healthsystem.common.security.BusinessPrincipal("another-org","demo-clinician","DEMO"),f.patient(),null,1,20).total()).isZero();
    }
}
