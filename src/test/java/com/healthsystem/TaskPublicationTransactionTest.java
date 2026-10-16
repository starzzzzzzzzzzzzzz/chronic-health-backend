package com.healthsystem;
import com.healthsystem.entity.AuditLog;
import com.healthsystem.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class TaskPublicationTransactionTest extends PublicationApiTestSupport {
 @MockitoSpyBean AuditLogMapper audit;
 @Test void auditFailureRollsBackPublicationInstancesAndOutbox() throws Exception {
  var r=approvedTask();String patient=r.bundle().path("patientId").asText();
  try {
   var pre=prepare(r,"NONE","2026-10-16T00:00:00Z");doThrow(new DataIntegrityViolationException("publication audit failure")).when(audit).insert(any(AuditLog.class));
   publish(r,pre,"rollback-key",409);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM publication WHERE task_bundle_id=?",Integer.class,r.bundle().path("id").asText())).isZero();
   assertThat(db.queryForObject("SELECT COUNT(*) FROM execution_task WHERE patient_id=?",Integer.class,patient)).isZero();
   assertThat(db.queryForObject("SELECT status FROM task_bundle WHERE id=?",String.class,r.bundle().path("id").asText())).isEqualTo("APPROVED");
  } finally {reset(audit);cleanupTaskPatient(patient);}
 }
 @Test void concurrentRetriesCreateExactlyOnePublicationAndOneSetOfInstances() throws Exception {
  var r=approvedTask();var pool=Executors.newFixedThreadPool(2);String patient=r.bundle().path("patientId").asText();
  try {
   var pre=prepare(r,"NONE","2026-10-16T00:00:00Z");var latch=new CountDownLatch(1);
   var a=pool.submit(()->{latch.await();return publishConcurrent(r,pre,"race-key");});var b=pool.submit(()->{latch.await();return publishConcurrent(r,pre,"race-key");});latch.countDown();
   assertThat(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,200);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM publication WHERE task_bundle_id=?",Integer.class,r.bundle().path("id").asText())).isEqualTo(1);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM execution_task WHERE patient_id=?",Integer.class,patient)).isEqualTo(7);
  } finally {pool.shutdownNow();cleanupTaskPatient(patient);}
 }
 int publishConcurrent(Ready r,tools.jackson.databind.JsonNode pre,String key) throws Exception {
  var body=new LinkedHashMap<>(publicationIntent(r,pre.path("replacementScope").asText(),pre.path("effectiveAt").asText()));body.put("publicationPreviewId",pre.path("id").asText());
  return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/task-bundles/"+r.bundle().path("id").asText()+"/publications").header("Authorization",TOKEN).header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
 }
}
