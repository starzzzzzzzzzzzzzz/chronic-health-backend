package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"health.demo.enabled=true","health.demo.token=development-test-token-only","health.demo.reviewer-token=development-reviewer-token-only","health.demo.patient-token=development-patient-token-only"})
@org.springframework.context.annotation.Import(PublicationApiTestSupport.TimeConfiguration.class)
abstract class PublicationApiTestSupport extends TaskApiTestSupport {
 @org.springframework.boot.test.context.TestConfiguration static class TimeConfiguration {
  @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary MutableWorkflowClock testWorkflowClock(){return new MutableWorkflowClock();}
 }
 @org.springframework.beans.factory.annotation.Autowired MutableWorkflowClock workflowClock;
 @org.junit.jupiter.api.BeforeEach void resetWorkflowTime(){workflowClock.set("2026-10-16T00:00:00Z");}
 static final String PATIENT="development-patient-token-only";
 record Ready(JsonNode bundle,JsonNode preview,JsonNode review){}
 Ready approvedTask() throws Exception {
  var b=readyBundle();var p=preview(b);
  var s=postJson("/task-bundles/"+b.path("id").asText()+"/review-submissions",TOKEN,Map.of("expectedRevision","2","schedulePreviewId",p.path("id").asText()),201);
  var r=postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),201);
  b=getJson("/task-bundles/"+b.path("id").asText(),TOKEN,200);
  grant(b.path("patientId").asText(),"demo-patient","TASK_READ");grant(b.path("patientId").asText(),"demo-patient","TASK_FEEDBACK");return new Ready(b,p,r);
 }
 Map<String,Object> publicationIntent(Ready r,String scope,String effective){return Map.of("expectedRevision",r.bundle().path("rowVersion").asText(),"reviewSubmissionId",r.review().path("id").asText(),"schedulePreviewId",r.preview().path("id").asText(),"effectiveAt",effective,"replacementScope",scope);}
 JsonNode prepare(Ready r,String scope,String effective) throws Exception {return postJson("/task-bundles/"+r.bundle().path("id").asText()+"/publication-previews",TOKEN,publicationIntent(r,scope,effective),201);}
 JsonNode publish(Ready r,JsonNode preview,String key,int status) throws Exception {
  var body=new LinkedHashMap<>(publicationIntent(r,preview.path("replacementScope").asText(),preview.path("effectiveAt").asText()));body.put("publicationPreviewId",preview.path("id").asText());
  return publishBody(r,body,key,status);
 }
 JsonNode publishBody(Ready r,Object body,String key,int code) throws Exception {
  var result=mvc.perform(post("/task-bundles/"+r.bundle().path("id").asText()+"/publications").header("Authorization",TOKEN).header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().is(code)).andReturn().getResponse();
  if(code==201)assertThat(result.getHeader("Location")).contains("/publications/");return json.readTree(result.getContentAsString()).path("data");
 }
 void cleanupTaskPatient(String patient) {
  new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status->{
   db.update("UPDATE task_bundle SET latest_preview_id=NULL WHERE patient_id=?",patient);
   for(String table:List.of("task_safety_event","task_result","task_feedback"))if(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Integer.class,table)>0)db.update("DELETE x FROM "+table+" x JOIN execution_task t ON t.id=x.execution_task_id WHERE t.patient_id=?",patient);
   db.update("DELETE x FROM outbox_event x JOIN publication p ON p.id=x.publication_id WHERE p.patient_id=?",patient);
   db.update("DELETE a FROM audit_log a JOIN execution_task t ON a.target_id=t.id WHERE t.patient_id=?",patient);
   db.update("DELETE FROM execution_task WHERE patient_id=?",patient);
   db.update("DELETE x FROM publication_request x JOIN publication p ON p.id=x.publication_id WHERE p.patient_id=?",patient);
   db.update("DELETE FROM publication WHERE patient_id=?",patient);
   db.update("DELETE x FROM publication_preview x JOIN task_bundle b ON b.id=x.task_bundle_id WHERE b.patient_id=?",patient);
   db.update("DELETE d FROM review_decision d JOIN review_submission s ON s.id=d.submission_id JOIN task_bundle b ON b.id=s.task_bundle_id WHERE b.patient_id=?",patient);
   db.update("DELETE s FROM review_submission s JOIN task_bundle b ON b.id=s.task_bundle_id WHERE b.patient_id=?",patient);
   db.update("DELETE x FROM schedule_preview x JOIN task_bundle b ON b.id=x.task_bundle_id WHERE b.patient_id=?",patient);
   for(String table:List.of("task_group_source","task_group_variant"))db.update("DELETE x FROM "+table+" x JOIN task_group g ON g.id=x.task_group_id JOIN task_bundle b ON b.id=g.task_bundle_id WHERE b.patient_id=?",patient);
   db.update("DELETE g FROM task_group g JOIN task_bundle b ON b.id=g.task_bundle_id WHERE b.patient_id=?",patient);
   db.update("UPDATE task_bundle SET based_on_bundle_id=NULL WHERE patient_id=?",patient);
   db.update("DELETE a FROM audit_log a JOIN task_bundle b ON a.target_id=b.id WHERE b.patient_id=?",patient);
   db.update("DELETE FROM task_bundle WHERE patient_id=?",patient);
   var plan=db.queryForMap("SELECT v.id,v.plan_id FROM care_plan_version v JOIN care_plan p ON p.id=v.plan_id WHERE p.patient_id=? LIMIT 1",patient);
   cleanup(new Fixture(patient,"",plan.get("id").toString(),plan.get("plan_id").toString(),""));
  });
 }
}
