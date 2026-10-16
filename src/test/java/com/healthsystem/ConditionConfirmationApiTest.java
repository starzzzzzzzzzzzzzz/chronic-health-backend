package com.healthsystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class ConditionConfirmationApiTest extends ReviewApiTestSupport {
    @Test void derivesRequirementsAndAppendsHistoryWithoutChangingPlanContent() throws Exception {
        var f=draft("routine",true);
        var before=getJson(conditions(f),TOKEN,200);
        assertThat(before.path("conditions").size()).isEqualTo(1);
        assertThat(before.path("conditions").get(0).path("status").asText()).isEqualTo("MISSING");
        var first=confirm(f,"1");
        assertThat(first.path("rowVersion").asText()).isEqualTo("2");
        assertThat(first.path("contentRevision").asText()).isEqualTo("1");
        assertThat(first.path("conditions").get(0).path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(first.path("history").get(0).path("confirmedBy").asText()).isEqualTo("demo-clinician");
        var second=confirm(f,"2");
        assertThat(second.path("history").size()).isEqualTo(2);
        assertThat(second.path("conditions").get(0).path("record").path("supersedesConfirmationId").asText()).isEqualTo(first.path("history").get(0).path("id").asText());
        postJson(conditions(f),TOKEN,Map.of("expectedRevision","1","entries",List.of(executionEntry("execution."+f.item()))),409);
    }
    @Test void contentChangeMakesOldConfirmationStaleUntilExplicitlyReconfirmed() throws Exception {
        var f=draft("routine",true);confirm(f,"1");
        patchJson(f.version(),Map.of("expectedRevision","2","title","修改后的方案"),200);
        var stale=getJson(conditions(f),TOKEN,200);
        assertThat(stale.path("conditions").get(0).path("status").asText()).isEqualTo("STALE");
        assertThat(stale.path("history").size()).isEqualTo(1);
        var fresh=confirm(f,"3");
        assertThat(fresh.path("conditions").get(0).path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(fresh.path("contentRevision").asText()).isEqualTo("2");
    }
    @Test void invalidBatchCannotPartiallyConfirmOrAdvanceRevision() throws Exception {
        var f=draft("routine",true);
        postJson(conditions(f),TOKEN,Map.of("expectedRevision","1","entries",List.of(executionEntry("execution."+f.item()),executionEntry("execution.foreign-item"))),400);
        assertThat(getJson(conditions(f),TOKEN,200).path("history").size()).isZero();
        assertThat(getJson("/plan-versions/"+f.version(),TOKEN,200).path("rowVersion").asText()).isEqualTo("1");
        var altered=new HashMap<>(executionEntry("execution."+f.item()));altered.put("confirmedBy","fake-doctor");
        postJson(conditions(f),TOKEN,Map.of("expectedRevision","1","entries",List.of(altered)),400);
        altered.remove("confirmedBy");altered.put("value",Map.of("actorType","CAREGIVER","available",true,"environmentConfirmed",true));
        postJson(conditions(f),TOKEN,Map.of("expectedRevision","1","entries",List.of(altered)),400);
        postJson(conditions(f),TOKEN,Map.of("expectedRevision","1","entries",Arrays.asList((Object)null)),400);
    }
    @Test void doesNotInventRequirementsForInformationOnlyItems() throws Exception {
        var f=draft("routine",false);
        assertThat(getJson(conditions(f),TOKEN,200).path("conditions").size()).isZero();
    }
    @Test void checksPatientCapabilitiesModeAndOrganizationAtEveryNewRoute() throws Exception {
        var f=draft("routine",true);
        postJson(conditions(f),REVIEWER,Map.of("expectedRevision","1","entries",List.of(executionEntry("execution."+f.item()))),403);
        db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=? AND capability='PLAN_CONFIRM'",f.patient());
        postJson(conditions(f),TOKEN,Map.of("expectedRevision","1","entries",List.of(executionEntry("execution."+f.item()))),403);
        db.update("UPDATE patient SET data_mode='REAL' WHERE id=?",f.patient());
        getJson(conditions(f),TOKEN,404);
        db.update("UPDATE patient SET data_mode='DEMO' WHERE id=?",f.patient());
        db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=? AND capability='PATIENT_READ'",f.patient());
        getJson(conditions(f),TOKEN,403);
        getJson("/plan-versions/"+UUID.randomUUID()+"/condition-confirmations",TOKEN,404);
    }
    @Test void medicationImportRequiresTrustedSnapshotSourceAndForbidsValueRewrite() throws Exception {
        String p=patient("demo-org","DEMO","用药虚构患者",true);
        String order=fact(p,"H","medication_order","{\"drugName\":\"虚构药物A\",\"doseText\":\"遵原医嘱\",\"timingText\":\"原医嘱时间\"}",null,null);
        db.update("UPDATE health_fact SET confirmation_status='CONFIRMED_PROFESSIONAL',confirmed_by='fixture-doctor',confirmed_at=UTC_TIMESTAMP(6) WHERE id=?",order);
        for(String cap:List.of("PLAN_CONFIRM","PLAN_CONFIRM_PROFESSIONAL"))grant(p,"demo-clinician",cap);
        var created=postJson("/care-plans",TOKEN,Map.of("patientId",p,"expectedProfileRevision","1","title","医嘱引用演示","startDate","2026-10-17","endDate","2026-10-23","zoneId","Asia/Shanghai"),201);
        String v=created.path("versionId").asText();
        var saved=patchJson(v,Map.of("expectedRevision","0","primaryGoal","遵循已有医嘱","modules",List.of(Map.of("moduleCode","C","selectionReason","已有医嘱","factIds",List.of(order))),"dimensions",List.of(Map.of("dimensionCode","medication","decision","ENABLED")),"items",List.of(Map.of("dimensionCode","medication","title","遵原医嘱","content","原医嘱内容不改写","actorType","PATIENT","executionGoal",true,"basis",List.of(Map.of("moduleCode","C","factId",order))))),200);
        String key="medication.source."+saved.path("items").get(0).path("id").asText();
        String route="/plan-versions/"+v+"/condition-confirmations";
        var entry=new HashMap<String,Object>(Map.of("conditionKey",key,"status","CONFIRMED","sourceFactId",order,"confirmationNote","已核对已有医嘱原文"));
        entry.put("value",Map.of("doseText","篡改剂量"));postJson(route,TOKEN,Map.of("expectedRevision","1","entries",List.of(entry)),400);entry.remove("value");
        String outside=fact(p,"H","medication_order","{}",null,null);entry.put("sourceFactId",outside);
        postJson(route,TOKEN,Map.of("expectedRevision","1","entries",List.of(entry)),400);entry.put("sourceFactId",order);
        db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=? AND capability='PLAN_CONFIRM_PROFESSIONAL'",p);
        postJson(route,TOKEN,Map.of("expectedRevision","1","entries",List.of(entry)),403);
        db.update("UPDATE patient_access_grant SET revoked_at=NULL WHERE patient_id=? AND capability='PLAN_CONFIRM_PROFESSIONAL'",p);
        var confirmed=postJson(route,TOKEN,Map.of("expectedRevision","1","entries",List.of(entry)),201);
        assertThat(confirmed.path("history").get(0).path("value").path("doseText").asText()).isEqualTo("遵原医嘱");
        assertThat(confirmed.path("history").get(0).path("sourceFactId").asText()).isEqualTo(order);
    }
}
