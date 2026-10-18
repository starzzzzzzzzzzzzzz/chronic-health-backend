package com.healthsystem;
import org.junit.jupiter.api.Test;
import com.healthsystem.entity.AuditLog;
import com.healthsystem.mapper.AuditLogMapper;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class TaskFeedbackTransactionTest extends FeedbackApiTestSupport {
 @MockitoSpyBean AuditLogMapper audit;
 @Test void auditFailureRollsBackFeedbackResultAndSafetyEvent() throws Exception {
  var t=publishedTask();try {
   doThrow(new DataIntegrityViolationException("feedback audit failure")).when(audit).insert(any(AuditLog.class));feedback(checkins(t),PATIENT,"feedback-failure",Map.of("completion","STOPPED"),409);
   for(String table:List.of("task_feedback","task_result","task_safety_event"))assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE execution_task_id=?",Integer.class,t.path("id").asText())).isZero();
  } finally {reset(audit);cleanupTaskPatient(t.path("patientId").asText());}
 }
 @Test void concurrentMainSubmissionsNeverCreateTwoExecutionResults() throws Exception {
  var t=publishedTask();var pool=Executors.newFixedThreadPool(2);try {
   var latch=new CountDownLatch(1);var a=pool.submit(()->{latch.await();return concurrentFeedback(t.path("id").asText(),"feedback-race-a");});var b=pool.submit(()->{latch.await();return concurrentFeedback(t.path("id").asText(),"feedback-race-b");});latch.countDown();
   assertThat(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM task_result WHERE execution_task_id=?",Integer.class,t.path("id").asText())).isEqualTo(1);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM task_feedback WHERE execution_task_id=?",Integer.class,t.path("id").asText())).isEqualTo(1);
  } finally {pool.shutdownNow();cleanupTaskPatient(t.path("patientId").asText());}
 }
 @Test void concurrentAmendmentsUseOneResultRevisionAndPreserveTheOriginal() throws Exception {
  var t=publishedTask();var pool=Executors.newFixedThreadPool(2);try {
   var initial=feedback(checkins(t),PATIENT,"original-before-race",Map.of(),201);var latch=new CountDownLatch(1);String route="/check-ins/"+initial.path("id").asText()+"/amendments";
   var a=pool.submit(()->{latch.await();return amendmentResponse(route,"amend-race-a");});var b=pool.submit(()->{latch.await();return amendmentResponse(route,"amend-race-b");});latch.countDown();
   assertThat(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM task_feedback WHERE execution_task_id=?",Integer.class,t.path("id").asText())).isEqualTo(2);
   assertThat(db.queryForObject("SELECT row_version FROM task_result WHERE execution_task_id=?",Long.class,t.path("id").asText())).isEqualTo(1);
  } finally {pool.shutdownNow();cleanupTaskPatient(t.path("patientId").asText());}
 }
 int amendmentResponse(String route,String key) throws Exception {return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(route).header("Authorization",PATIENT).header("Idempotency-Key",key).contentType("application/json").content("{\"expectedRevision\":\"0\",\"completion\":\"PARTIAL\"}")).andReturn().getResponse().getStatus();}
 int concurrentFeedback(String task,String key) throws Exception {return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/execution-tasks/"+task+"/check-ins").header("Authorization",PATIENT).header("Idempotency-Key",key).contentType("application/json").content("{}")).andReturn().getResponse().getStatus();}
}
