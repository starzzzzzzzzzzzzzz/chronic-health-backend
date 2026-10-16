package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Transactional
class TaskPublicationApiTest extends PublicationApiTestSupport {
 @Test void patientSeesOnlyPublishedFrozenOccurrencesAndPublicationIsIdempotent() throws Exception {
  var r=approvedTask();String patient=r.bundle().path("patientId").asText();
  assertThat(getJson("/patients/"+patient+"/execution-tasks?date=2026-10-17",PATIENT,200).isEmpty()).isTrue();
  var pre=prepare(r,"NONE","2026-10-16T00:00:00Z");var published=publish(r,pre,"pub-key-one",201);
  assertThat(published.path("notificationStatus").asText()).isEqualTo("NOT_CONFIGURED");
  assertThat(publish(r,pre,"pub-key-one",200).path("id")).isEqualTo(published.path("id"));
  assertThat(publish(r,pre,"pub-key-two",200).path("id")).isEqualTo(published.path("id"));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM execution_task WHERE publication_id=?",Integer.class,published.path("id").asText())).isEqualTo(7);
  var day=getJson("/patients/"+patient+"/execution-tasks?date=2026-10-17",PATIENT,200);assertThat(day.size()).isEqualTo(1);
  assertThat(day.get(0).path("contentSnapshot")).isEqualTo(r.preview().path("occurrences").get(0).path("contentSnapshot"));
  getJson("/execution-tasks/"+day.get(0).path("id").asText(),PATIENT,200);
  db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=? AND subject_id='demo-patient'",patient);
  getJson("/execution-tasks/"+day.get(0).path("id").asText(),PATIENT,403);
 }
 @Test void publicationRejectsWrongReviewAndConflictingIdempotencyPayload() throws Exception {
  var r=approvedTask();var bad=new LinkedHashMap<>(publicationIntent(r,"NONE","2026-10-16T00:00:00Z"));bad.put("reviewSubmissionId",UUID.randomUUID().toString());
  postJson("/task-bundles/"+r.bundle().path("id").asText()+"/publication-previews",TOKEN,bad,409);
  var pre=prepare(r,"NONE","2026-10-16T00:00:00Z");publish(r,pre,"conflict-key",201);
  bad=new LinkedHashMap<>(publicationIntent(r,"NONE","2026-10-16T01:00:00Z"));bad.put("publicationPreviewId",pre.path("id").asText());publishBody(r,bad,"conflict-key",409);
 }
 @Test void futureReplacementRetainsStartedTasksAndDoesNotCancelBeforeBoundary() throws Exception {
  var old=approvedTask();var pub=publish(old,prepare(old,"NONE","2026-10-16T00:00:00Z"),"old-key",201);
  var newer=postJson("/task-bundles/"+old.bundle().path("id").asText()+"/versions",TOKEN,Map.of("expectedRevision","5","changeReason","仅更新执行排程"),201);
  newer=configure(newer,List.of(slot("morning","09:00",0,60)));var preview=preview(newer);
  var review=postJson("/task-bundles/"+newer.path("id").asText()+"/review-submissions",TOKEN,Map.of("expectedRevision","2","schedulePreviewId",preview.path("id").asText()),201);
  review=postJson("/review-submissions/"+review.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),201);
  newer=getJson("/task-bundles/"+newer.path("id").asText(),TOKEN,200);var r=new Ready(newer,preview,review);
  var pre=prepare(r,"ALL_FUTURE","2026-10-18T00:00:00Z");assertThat(pre.path("replaceTaskIds").size()).isEqualTo(5); // Oct18 08:00 has already opened at the boundary.
  publish(r,pre,"new-key",201);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM execution_task WHERE publication_id=? AND cancelled_at IS NOT NULL",Integer.class,pub.path("id").asText())).isEqualTo(5);
  assertThat(getJson("/patients/"+newer.path("patientId").asText()+"/execution-tasks?date=2026-10-19",PATIENT,200).size()).isEqualTo(1); // Old stays visible until future effectiveAt; new is hidden.
  workflowClock.set("2026-10-19T00:00:00Z");var active=getJson("/patients/"+newer.path("patientId").asText()+"/execution-tasks?date=2026-10-19",PATIENT,200);assertThat(active.size()).isEqualTo(1);assertThat(active.get(0).path("taskBundleId")).isEqualTo(newer.path("id"));
  assertThat(getJson("/patients/"+newer.path("patientId").asText()+"/execution-tasks?date=2026-10-17",PATIENT,200).size()).isEqualTo(1);
 }
 @Test void sourceOrScheduleCorruptionCannotBePublishedAndPatientCannotPublish() throws Exception {
  var r=approvedTask();var pre=prepare(r,"NONE","2026-10-16T00:00:00Z");
  postJson("/task-bundles/"+r.bundle().path("id").asText()+"/publication-previews",PATIENT,publicationIntent(r,"NONE","2026-10-16T00:00:00Z"),403);
  db.update("UPDATE schedule_preview SET preview_hash=? WHERE id=?","1".repeat(64),r.preview().path("id").asText());publish(r,pre,"corrupt-preview",409);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM publication WHERE task_bundle_id=?",Integer.class,r.bundle().path("id").asText())).isZero();
 }
}
