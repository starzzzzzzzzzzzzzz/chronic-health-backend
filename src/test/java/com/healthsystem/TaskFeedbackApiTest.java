package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@Transactional
class TaskFeedbackApiTest extends FeedbackApiTestSupport {
 @Test void emptyOneClickMeansSelfReportedCompletionWithoutInventingTimingOrSafety() throws Exception {
  var t=publishedTask();feedback(checkins(t),TOKEN,"staff-forbidden",Map.of(),403);
  var f=feedback(checkins(t),PATIENT,"one-click",Map.of(),201);var a=f.path("assessment");
  assertThat(a.path("completion").asText()).isEqualTo("COMPLETED");assertThat(a.path("completionRatio").intValue()).isEqualTo(100);
  assertThat(a.path("executionTiming").asText()).isEqualTo("UNKNOWN");assertThat(a.path("submissionSource").asText()).isEqualTo("NORMAL");assertThat(a.path("safetyStatus").asText()).isEqualTo("UNKNOWN");assertThat(a.path("qualityStatus").asText()).isEqualTo("NOT_APPLICABLE");
  assertThat(feedback(checkins(t),PATIENT,"one-click",Map.of(),200).path("id")).isEqualTo(f.path("id"));
  feedback(checkins(t),PATIENT,"different-key",Map.of(),409);feedback(checkins(t),PATIENT,"one-click",Map.of("completion","NOT_DONE"),409);
  assertThat(getJson("/execution-tasks/"+t.path("id").asText(),PATIENT,200).path("currentResult").path("rowVersion").asText()).isEqualTo("0");
 }
 @Test void makeupDoesNotSubstituteSubmissionTimeForActualExecutionTime() throws Exception {
  var t=publishedTask();workflowClock.set("2026-10-18T05:00:00Z");
  var f=feedback(checkins(t),PATIENT,"makeup",Map.of("actualExecutedAt","2026-10-17T00:20:00Z"),201);
  assertThat(f.path("assessment").path("submissionSource").asText()).isEqualTo("MAKEUP");assertThat(f.path("assessment").path("executionTiming").asText()).isEqualTo("ON_TIME");
  assertThat(f.path("receivedAt").asText()).isEqualTo("2026-10-18T05:00:00Z");
 }
 @Test void amendmentsAppendHistoryAndCannotClearAnUnresolvedSafetySignal() throws Exception {
  var t=publishedTask();var f=feedback(checkins(t),PATIENT,"stop",Map.of("completion","STOPPED","reportedSafety",true),201);
  String route="/check-ins/"+f.path("id").asText()+"/amendments";
  var amended=feedback(route,PATIENT,"amend",Map.of("expectedRevision","0","completion","COMPLETED","reportedSafety",false,"note","自述后续完成；原不适仍待核实"),201);
  assertThat(amended.path("assessment").path("completion").asText()).isEqualTo("COMPLETED");
  var task=getJson("/execution-tasks/"+t.path("id").asText(),PATIENT,200);assertThat(task.path("currentResult").path("assessment").path("safetyStatus").asText()).isEqualTo("REQUIRES_REVIEW");
  var history=getJson(checkins(t),PATIENT,200);assertThat(history.size()).isEqualTo(2);assertThat(history.get(0).path("assessment").path("completion").asText()).isEqualTo("STOPPED");
  assertThat(feedback(route,PATIENT,"amend",Map.of("expectedRevision","0","completion","COMPLETED","reportedSafety",false,"note","自述后续完成；原不适仍待核实"),200).path("id")).isEqualTo(amended.path("id"));
  feedback(route,PATIENT,"stale-amend",Map.of("expectedRevision","0","completion","PARTIAL"),409);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM task_safety_event WHERE execution_task_id=? AND status='OPEN'",Integer.class,t.path("id").asText())).isEqualTo(1);
 }
 @Test void completionChoicesDoNotRequireOptionalMeasurementsOrReasons() throws Exception {
  var t=publishedTask();var f=feedback(checkins(t),PATIENT,"partial",Map.of("completion","PARTIAL"),201);assertThat(f.path("assessment").path("completionRatio").intValue()).isEqualTo(50);
  f=feedback("/check-ins/"+f.path("id").asText()+"/amendments",PATIENT,"not-done",Map.of("expectedRevision","0","completion","NOT_DONE"),201);assertThat(f.path("assessment").path("completionRatio").intValue()).isZero();
 }
 @Test void openAndCutoffBoundariesAreExclusiveAtRightAndUnknownFeedbackCannotBeForged() throws Exception {
  var t=publishedTask();workflowClock.set("2026-10-16T23:59:59Z");feedback(checkins(t),PATIENT,"too-early",Map.of(),409);
  workflowClock.set("2026-10-17T00:30:00Z");feedback(checkins(t),PATIENT,"future-actual",Map.of("actualExecutedAt","2026-10-17T00:31:00Z"),400);
  feedback(checkins(t),PATIENT,"forged-quality",Map.of("qualityScore",100),400);
  workflowClock.set("2026-10-18T10:00:00Z");feedback(checkins(t),PATIENT,"at-cutoff",Map.of(),409);
 }
 @Test void revokedPatientGrantPreventsSubmissionAndIdempotentReplay() throws Exception {
  var t=publishedTask();feedback(checkins(t),PATIENT,"grant-key",Map.of(),201);
  db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=? AND subject_id='demo-patient' AND capability='TASK_FEEDBACK'",t.path("patientId").asText());feedback(checkins(t),PATIENT,"grant-key",Map.of(),403);
 }
 @Test void everyFrozenMakeupDeadlineAcceptsOneMicrosecondBeforeAndRejectsItsRightEndpoint() throws Exception {
  var deadlines=Map.of("medication","2026-10-17T13:00:00Z","diet","2026-10-18T16:00:00Z","exercise","2026-10-18T16:00:00Z","routine","2026-10-18T10:00:00Z","monitoring","2026-10-17T13:00:00Z","tcm","2026-10-17T07:00:00Z","other","2026-10-18T16:00:00Z");
  for(var entry:deadlines.entrySet()) {
   var t=publishedTask();var cutoff=java.time.Instant.parse(entry.getValue());
   db.update("UPDATE execution_task SET dimension_code=?,normal_cutoff_at=?,makeup_cutoff_at=? WHERE id=?",entry.getKey(),java.time.LocalDateTime.parse("2026-10-17T01:00:00"),java.time.LocalDateTime.ofInstant(cutoff,java.time.ZoneOffset.UTC),t.path("id").asText());
   workflowClock.set(cutoff.minusNanos(1000).toString());var f=feedback(checkins(t),PATIENT,"before-"+entry.getKey(),Map.of(),201);
   assertThat(f.path("assessment").path("submissionSource").asText()).isEqualTo("MAKEUP");
   workflowClock.set(cutoff.toString());feedback("/check-ins/"+f.path("id").asText()+"/amendments",PATIENT,"at-"+entry.getKey(),Map.of("expectedRevision","0","note","边界更正"),409);
  }
 }
}
