package com.healthsystem;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"health.demo.enabled=true", "health.demo.token=development-test-token-only", "health.demo.reviewer-token=development-reviewer-token-only"})
abstract class ReviewApiTestSupport extends DatabaseApiTestSupport {
    static final String REVIEWER="development-reviewer-token-only";
    record Fixture(String patient, String fact, String version, String plan, String item) {}
    JsonNode postJson(String route,String token,Object body,int expected) throws Exception {
        var result=mvc.perform(post(route).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andExpect(status().is(expected)).andReturn().getResponse();
        if(expected==201) org.assertj.core.api.Assertions.assertThat(result.getHeader("Location")).isNotBlank();
        return json.readTree(result.getContentAsString()).path("data");
    }
    JsonNode getJson(String route,String token,int expected) throws Exception {
        return json.readTree(mvc.perform(get(route).header("Authorization",token)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).path("data");
    }
    JsonNode patchJson(String version,Object body,int expected) throws Exception {
        return json.readTree(mvc.perform(patch("/plan-versions/"+version).header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).path("data");
    }
    void grant(String patient,String subject,String cap) {
        db.update("INSERT INTO patient_access_grant(id,org_id,created_at,created_by,updated_at,row_version,patient_id,subject_id,capability,valid_from) VALUES(?,'demo-org',UTC_TIMESTAMP(6),'test',UTC_TIMESTAMP(6),0,?,?,?,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY))",UUID.randomUUID().toString(),patient,subject,cap);
    }
    Fixture draft(String dimension, boolean execution) throws Exception {
        String p=patient("demo-org","DEMO","第二批虚构测试患者",true);
        String f=fact(p,"B","routine","{\"valueType\":\"TEXT\",\"value\":\"晚睡\"}",null,null);
        for(String cap:List.of("PLAN_CONFIRM","PLAN_CONFIRM_PROFESSIONAL")) grant(p,"demo-clinician",cap);
        for(String cap:List.of("PATIENT_READ","PLAN_REVIEW")) grant(p,"demo-reviewer",cap);
        var created=postJson("/care-plans",TOKEN,Map.of("patientId",p,"expectedProfileRevision","1","title","虚构周期方案","startDate","2026-10-17","endDate","2026-10-23","zoneId","Asia/Shanghai"),201);
        String v=created.path("versionId").asText();
        var saved=patchJson(v,Map.of("expectedRevision","0","primaryGoal","建立规律作息","goalBasis",Map.of("factIds",List.of(f)),
            "modules",List.of(Map.of("moduleCode","B0","selectionReason","作息目标","factIds",List.of(f))),
            "dimensions",List.of(Map.of("dimensionCode",dimension,"decision","ENABLED")),
            "items",List.of(Map.of("dimensionCode",dimension,"title","演示建议","content","虚构演示候选，尚未审核","actorType","PATIENT","executionGoal",execution,"basis",List.of(Map.of("moduleCode","B0","factId",f))))),200);
        return new Fixture(p,f,v,created.path("planId").asText(),saved.path("items").get(0).path("id").asText());
    }
    String conditions(Fixture f){return "/plan-versions/"+f.version()+"/condition-confirmations";}
    Map<String,Object> executionEntry(String key) {
        return Map.of("conditionKey",key,"status","CONFIRMED","value",Map.of("actorType","PATIENT","available",true,"environmentConfirmed",true),"confirmationNote","已人工核实执行主体与环境，仅用于虚构联调");
    }
    String submissions(Fixture f){return "/plan-versions/"+f.version()+"/review-submissions";}
    JsonNode submit(Fixture f,String revision) throws Exception {
        return postJson(submissions(f),TOKEN,Map.of("expectedRevision",revision),201);
    }
    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    void cleanup(Fixture f) {
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status->cleanupRows(f));
    }
    private void cleanupRows(Fixture f) {
        db.update("DELETE d FROM review_decision d JOIN review_submission s ON s.id=d.submission_id WHERE s.plan_version_id=?",f.version());
        db.update("DELETE FROM review_submission WHERE plan_version_id=?",f.version());
        db.update("DELETE FROM condition_confirmation WHERE plan_version_id=?",f.version());
        db.update("DELETE b FROM plan_item_basis b JOIN care_plan_item i ON b.plan_item_id=i.id WHERE i.plan_version_id=?",f.version());
        for(String table:List.of("care_plan_item","care_plan_dimension","care_plan_module","plan_management_focus"))db.update("DELETE FROM "+table+" WHERE plan_version_id=?",f.version());
        db.update("DELETE FROM audit_log WHERE target_id=?",f.version());
        db.update("UPDATE care_plan SET current_version_id=NULL WHERE id=?",f.plan());
        db.update("DELETE FROM care_plan_version WHERE id=?",f.version());
        db.update("DELETE FROM care_plan WHERE id=?",f.plan());
        db.update("DELETE FROM health_input_snapshot WHERE patient_id=?",f.patient());
        db.update("DELETE FROM health_fact WHERE patient_id=?",f.patient());
        db.update("DELETE FROM patient_access_grant WHERE patient_id=?",f.patient());
        db.update("DELETE FROM patient WHERE id=?",f.patient());
    }
    JsonNode confirm(Fixture f,String revision) throws Exception {
        return postJson(conditions(f),TOKEN,Map.of("expectedRevision",revision,"entries",List.of(executionEntry("execution."+f.item()))),201);
    }
}
