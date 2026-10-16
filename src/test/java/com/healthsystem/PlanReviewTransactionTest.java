package com.healthsystem;
import com.healthsystem.entity.AuditLog;
import com.healthsystem.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class PlanReviewTransactionTest extends ReviewApiTestSupport {
    @MockitoSpyBean AuditLogMapper audit;
    int response(String route,String token,Map<String,Object> body,CountDownLatch start) throws Exception {
        start.await();return mvc.perform(post(route).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
    }
    @Test void concurrentSubmissionsAndDecisionsProduceOnlyOneWinner() throws Exception {
        var f=draft("routine",true);var pool=Executors.newFixedThreadPool(2);
        try {
            confirm(f,"1");var latch=new CountDownLatch(1);
            var a=pool.submit(()->response(submissions(f),TOKEN,Map.of("expectedRevision","2"),latch));
            var b=pool.submit(()->response(submissions(f),TOKEN,Map.of("expectedRevision","2"),latch));latch.countDown();
            assertThat(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            String s=db.queryForObject("SELECT id FROM review_submission WHERE plan_version_id=?",String.class,f.version());
            var decisionLatch=new CountDownLatch(1);String route="/review-submissions/"+s+"/decisions";
            var c=pool.submit(()->response(route,REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),decisionLatch));
            var d=pool.submit(()->response(route,REVIEWER,Map.of("expectedRevision","0","decision","RETURN","comment","演示退回"),decisionLatch));decisionLatch.countDown();
            assertThat(List.of(c.get(60,TimeUnit.SECONDS),d.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM review_decision WHERE submission_id=?",Integer.class,s)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT content_revision FROM care_plan_version WHERE id=?",Long.class,f.version())).isEqualTo(1);
        } finally {pool.shutdownNow();cleanup(f);}
    }
    @Test void auditFailureRollsBackSubmittedSnapshotAndParentState() throws Exception {
        var f=draft("routine",false);
        try {
            doThrow(new DataIntegrityViolationException("simulated audit failure")).when(audit).insert(any(AuditLog.class));
            postJson(submissions(f),TOKEN,Map.of("expectedRevision","1"),409);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM review_submission WHERE plan_version_id=?",Integer.class,f.version())).isZero();
            assertThat(db.queryForObject("SELECT status FROM care_plan_version WHERE id=?",String.class,f.version())).isEqualTo("DRAFT");
            assertThat(db.queryForObject("SELECT row_version FROM care_plan_version WHERE id=?",Long.class,f.version())).isEqualTo(1);
        } finally {reset(audit);cleanup(f);}
    }
    @Test void auditFailureRollsBackDecisionAndBothStates() throws Exception {
        var f=draft("routine",false);
        try {
            var s=submit(f,"1");String id=s.path("id").asText();
            doThrow(new DataIntegrityViolationException("simulated audit failure")).when(audit).insert(any(AuditLog.class));
            postJson("/review-submissions/"+id+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),409);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM review_decision WHERE submission_id=?",Integer.class,id)).isZero();
            assertThat(db.queryForObject("SELECT status FROM review_submission WHERE id=?",String.class,id)).isEqualTo("PENDING");
            assertThat(db.queryForObject("SELECT status FROM care_plan_version WHERE id=?",String.class,f.version())).isEqualTo("IN_REVIEW");
        } finally {reset(audit);cleanup(f);}
    }
}
