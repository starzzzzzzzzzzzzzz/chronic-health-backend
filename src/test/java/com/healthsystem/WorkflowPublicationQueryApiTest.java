package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@Transactional
class WorkflowPublicationQueryApiTest extends PublicationApiTestSupport {
 @Test void publishedGraphCanBeRecoveredWithoutBrowserIds() throws Exception {
  var r=approvedTask();var preview=prepare(r,"NONE","2026-10-17T00:00:00Z");var pub=publish(r,preview,UUID.randomUUID().toString(),201);
  String v=r.bundle().path("sourcePlanVersionId").asText();
  var w=getJson("/plan-versions/"+v+"/workflow",REVIEWER,200);
  assertThat(w.path("taskBundles").get(0).path("id").asText()).isEqualTo(r.bundle().path("id").asText());
  assertThat(w.path("publications").get(0).path("id").asText()).isEqualTo(pub.path("id").asText());
  assertThat(w.path("reviewSubmissions").size()).isEqualTo(2);
  db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=? AND subject_id='demo-reviewer' AND capability='TASK_READ'",r.bundle().path("patientId").asText());
  getJson("/plan-versions/"+v+"/workflow",REVIEWER,403);
 }
 @Test void taskPendingQueueMatchesDetailReadPermissions() throws Exception {
  var b=readyBundle();var preview=preview(b);
  var s=postJson("/task-bundles/"+b.path("id").asText()+"/review-submissions",TOKEN,Map.of("expectedRevision","2","schedulePreviewId",preview.path("id").asText()),201);
  String id=s.path("id").asText();
  assertThat(getJson("/review-submissions?stage=TASK",REVIEWER,200).toString()).contains(id);
  db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=? AND subject_id='demo-reviewer' AND capability='TASK_READ'",b.path("patientId").asText());
  assertThat(getJson("/review-submissions?stage=TASK",REVIEWER,200).toString()).doesNotContain(id);
 }
}
