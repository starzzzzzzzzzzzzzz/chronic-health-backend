package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@Transactional
class TaskSchedulePolicyTest extends TaskApiTestSupport {
    @Test void medicationUsesTheEntireConfirmedOrderScheduleAndRejectsReminderRewriting() throws Exception {
        String patient=patient("demo-org","DEMO","虚构医嘱来源测试",true);
        var schedule=Map.of("isoWeekdays",List.of(1,2,3,4,5,6,7),"slots",List.of(slot("dose","08:00",0,60)),"rotationPolicy","FIXED");
        var order=Map.of("valueType","OBJECT","value",Map.of("drugName","虚构药物不可用于真实用药","dose","原医嘱演示量","route","原医嘱途径","frequency","原医嘱频次","mealRelation","原医嘱餐关系","schedule",schedule));
        String fact=fact(patient,"H","medication_order",json.writeValueAsString(order),null,null);
        db.update("UPDATE health_fact SET confirmation_status='CONFIRMED_PROFESSIONAL',confirmed_by='demo-source',confirmed_at=UTC_TIMESTAMP(6) WHERE id=?",fact);
        for(String cap:List.of("PLAN_CONFIRM","PLAN_CONFIRM_PROFESSIONAL","TASK_EDIT","TASK_READ"))grant(patient,"demo-clinician",cap);
        for(String cap:List.of("PATIENT_READ","PLAN_REVIEW"))grant(patient,"demo-reviewer",cap);
        var created=postJson("/care-plans",TOKEN,Map.of("patientId",patient,"expectedProfileRevision","1","title","虚构医嘱确认测试","startDate","2026-10-17","endDate","2026-10-23","zoneId","Asia/Shanghai"),201);
        String version=created.path("versionId").asText();
        var changed=patchJson(version,Map.of("expectedRevision","0","primaryGoal","保留已有确认医嘱","goalBasis",Map.of("factIds",List.of(fact)),"modules",List.of(Map.of("moduleCode","C","selectionReason","保留已确认医嘱","factIds",List.of(fact))),"dimensions",List.of(Map.of("dimensionCode","medication","decision","ENABLED")),"items",List.of(Map.of("dimensionCode","medication","title","原医嘱执行","content","仅执行已有虚构医嘱内容","actorType","PATIENT","executionGoal",true,"basis",List.of(Map.of("moduleCode","C","factId",fact))))),200);
        String item=changed.path("items").get(0).path("id").asText();
        postJson("/plan-versions/"+version+"/condition-confirmations",TOKEN,Map.of("expectedRevision","1","entries",List.of(executionEntry("execution."+item),Map.of("conditionKey","medication.source."+item,"status","CONFIRMED","sourceFactId",fact,"confirmationNote","只引用确认来源"))),201);
        var s=postJson("/plan-versions/"+version+"/review-submissions",TOKEN,Map.of("expectedRevision","2"),201);
        postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),201);
        var b=postJson("/plan-versions/"+version+"/task-bundles",TOKEN,Map.of("expectedRevision","4"),201);
        assertThat(b.path("groups").get(0).path("professionalBinding").path("dose").asText()).isEqualTo("原医嘱演示量");
        assertThat(b.path("issues").toString()).doesNotContain("TRUSTED_MEDICATION_SCHEDULE_REQUIRED");
        b=configure(b,List.of(slot("dose","08:00",0,60)));assertThat(preview(b).path("issues").isEmpty()).isTrue();
        b=getJson("/task-bundles/"+b.path("id").asText(),TOKEN,200);
        var altered=Map.of("isoWeekdays",List.of(1,2,3,4,5,6,7),"slots",List.of(slot("dose","09:00",0,60)),"rotationPolicy","FIXED");
        b=editBundle(b.path("id").asText(),Map.of("expectedRevision",b.path("rowVersion").asText(),"groups",List.of(Map.of("id",b.path("groups").get(0).path("id").asText(),"schedule",altered))),200);
        assertThat(preview(b).path("issues").toString()).contains("MEDICATION_SCHEDULE_CHANGED");
    }
    @Test void approvedDemoCandidatePoolRotatesDeterministicallyWithoutConsecutiveRepeats() throws Exception {
        var template=db.queryForMap("SELECT id,content_json FROM task_template WHERE dimension_code='diet' AND org_id='demo-org' LIMIT 1");
        var content=(tools.jackson.databind.node.ObjectNode)json.readTree(template.get("content_json").toString());
        content.set("variants",json.valueToTree(List.of(Map.of("variantKey","A","title","虚构参考A","content","已确认演示内容A","parameters",Map.of()),Map.of("variantKey","B","title","虚构参考B","content","已确认演示内容B","parameters",Map.of()))));
        db.update("UPDATE task_template SET content_json=?,content_hash=? WHERE id=?",json.writeValueAsString(content),com.healthsystem.common.json.CanonicalJson.hash(content),template.get("id"));
        var f=approved("diet",true);var b=configure(bundle(f,"4"),List.of(slot("lunch","12:00",0,60)));
        var rule=(tools.jackson.databind.node.ObjectNode)b.path("groups").get(0).path("schedule").deepCopy();rule.put("rotationPolicy","ROTATE");
        b=editBundle(b.path("id").asText(),Map.of("expectedRevision","1","groups",List.of(Map.of("id",b.path("groups").get(0).path("id").asText(),"schedule",rule))),200);
        var p=preview(b);assertThat(p.path("issues").isEmpty()).isTrue();
        assertThat(p.path("occurrences").get(0).path("contentSnapshot").path("variant").path("variantKey").asText()).isEqualTo("A");
        assertThat(p.path("occurrences").get(1).path("contentSnapshot").path("variant").path("variantKey").asText()).isEqualTo("B");
        assertThat(p.path("occurrences").get(2).path("contentSnapshot").path("variant").path("variantKey").asText()).isEqualTo("A");
    }
    @Test void identicalNonMedicalExecutionItemsMergeButKeepBothSourceItems() throws Exception {
        var f=draft("routine",true);
        for(String cap:List.of("TASK_EDIT","TASK_READ"))grant(f.patient(),"demo-clinician",cap);
        var draft=getJson("/plan-versions/"+f.version(),TOKEN,200);
        var one=draft.path("items").get(0);var two=(tools.jackson.databind.node.ObjectNode)one.deepCopy();two.remove("id");
        var changed=patchJson(f.version(),Map.of("expectedRevision","1","items",List.of(one,two)),200);
        var entries=new ArrayList<Map<String,Object>>();for(var i:changed.path("items"))entries.add(executionEntry("execution."+i.path("id").asText()));
        postJson(conditions(f),TOKEN,Map.of("expectedRevision","2","entries",entries),201);
        var s=submit(f,"3");postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),201);
        var b=bundle(f,"5");assertThat(b.path("groups").size()).isEqualTo(1);assertThat(b.path("groups").get(0).path("sourcePlanItemIds").size()).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM task_group_source WHERE task_group_id=?",Integer.class,b.path("groups").get(0).path("id").asText())).isEqualTo(2);
    }
    @Test void nonExistentDaylightSavingTimeProducesABlockerInsteadOfSilentlyMovingTheTask() throws Exception {
        var f=approvedPeriod("routine","2026-03-29","2026-03-29","Europe/Berlin");
        var p=preview(configure(generateCurrent(f),List.of(slot("gap","02:30",0,60))));
        assertThat(p.path("issues").toString()).contains("AMBIGUOUS_LOCAL_TIME");assertThat(p.path("occurrences").isEmpty()).isTrue();
    }
    @Test void loadOverflowKeepsAllSafetyOccurrencesAndBlocksReview() throws Exception {
        var b=readyBundle();var slots=new ArrayList<Map<String,Object>>();for(int i=0;i<11;i++)slots.add(slot("s"+i,String.format("%02d:00",i),0,30));
        b=editBundle(b.path("id").asText(),Map.of("expectedRevision","1","groups",List.of(Map.of("id",b.path("groups").get(0).path("id").asText(),"priorityCode","L0","safetyFocus",true,"schedule",Map.of("isoWeekdays",List.of(1,2,3,4,5,6,7),"slots",slots,"rotationPolicy","FIXED")))),200);
        var p=preview(b);assertThat(p.path("occurrences").size()).isEqualTo(77);assertThat(p.path("issues").toString()).contains("DAILY_LOAD_EXCEEDED");
    }
    @Test void onlyProfessionallyConfirmedCareClassificationAllowsTwelveDailyTasks() {
        var b=new com.healthsystem.entity.TaskBundle();b.setId(UUID.randomUUID().toString());b.setPlanVersionId(UUID.randomUUID().toString());b.setSourceReviewSubmissionId(UUID.randomUUID().toString());b.setSourceReviewDecisionId(UUID.randomUUID().toString());b.setSourcePlanHash("0".repeat(64));b.setStartDate(java.time.LocalDate.parse("2026-10-17"));b.setEndDate(b.getStartDate());b.setZoneId("Asia/Shanghai");
        var slots=new ArrayList<Map<String,Object>>();for(int i=0;i<12;i++)slots.add(slot("s"+i,String.format("%02d:00",i),0,30));
        var g=json.valueToTree(Map.of("id","group","dimensionCode","other","variants",List.of(Map.of("id","variant")),"schedule",Map.of("isoWeekdays",List.of(6),"slots",slots)));
        var policy=new com.healthsystem.service.impl.TaskSchedulePolicy();
        b.setSourceSnapshotJson("{}");assertThat(policy.expand(b,List.of(g)).issues().toString()).contains("DAILY_LOAD_EXCEEDED");
        b.setSourceSnapshotJson("{\"plan\":{\"inputSnapshot\":{\"facts\":[{\"factCode\":\"functional_classification\",\"confirmationStatus\":\"CONFIRMED_PROFESSIONAL\",\"value\":{\"value\":\"CARE_DEPENDENT\"}}]}}}");
        assertThat(policy.expand(b,List.of(g)).issues()).isEmpty();
    }
    @Test void duplicateSlotKeysAndOverlappingWindowsAreNotSilentlyCollapsed() throws Exception {
        var f=approved("routine",true);var b=configure(bundle(f,"4"),List.of(slot("same","08:00",0,90),slot("same","09:00",0,90)));
        assertThat(preview(b).path("issues").toString()).contains("DUPLICATE_SLOT_KEY");
    }
}
