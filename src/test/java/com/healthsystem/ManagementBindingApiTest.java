package com.healthsystem;

import com.healthsystem.service.PlanAiClient;
import com.healthsystem.service.LegacyArchiveMappingService;
import com.healthsystem.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"health.demo.enabled=true", "health.demo.token=development-test-token-only",
        "health.ai.enabled=true", "health.ai.api-key=test-placeholder-never-sent", "health.ai.worker-enabled=false",
        "health.legacy.recommendation-wait-ms=0"})
@Transactional
class ManagementBindingApiTest extends DatabaseApiTestSupport {
    @Autowired LegacyArchiveMappingService mappings;
    @Autowired PlanGenerationWorker worker;
    @MockitoBean PlanAiClient client;
    record Fixture(String patient, String alias, String routine, String diabetes, String hypertension) {}
    Fixture fixture() {
        String p = patient("demo-org", "DEMO", "侧重绑定虚构患者", true);
        String routine = fact(p,"B","routine","{\"value\":\"晚睡\"}",null,null);
        String dm = diagnosis(p,"糖尿病"), ht = diagnosis(p,"高血压");
        return new Fixture(p,mappings.ensureForPatient("demo-org",p).toString(),routine,dm,ht);
    }
    String diagnosis(String p,String name) {
        String id=fact(p,"H","diagnosis","{\"name\":\""+name+"\"}",null,null);
        db.update("UPDATE health_fact SET confirmation_status='CONFIRMED_PROFESSIONAL',confirmed_by='test',confirmed_at=UTC_TIMESTAMP(6) WHERE id=?",id);
        return id;
    }
    JsonNode postJson(String route,Object body,int status) throws Exception {
        return json.readTree(mvc.perform(post(route).header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().is(status)).andReturn().getResponse().getContentAsString()).path("data");
    }
    JsonNode evaluate(Fixture f) throws Exception {
        return postJson("/store/care/drafts/basis/evaluate",Map.of("archiveId",f.alias()),200);
    }
    ObjectNode confirmed(JsonNode evaluation,String disease,String subtype,String priority) {
        JsonNode baseline=null;
        for(var d:evaluation.path("diseases"))if(disease.equals(d.path("name").asText()))baseline=d;
        assertThat(baseline).isNotNull();
        return (ObjectNode)json.valueToTree(Map.of("sourceHash",evaluation.path("sourceHash").asText(),"evaluation",baseline,
                "primarySubtype",subtype,"secondarySubtypes",List.of(),"priority",priority,"adjustmentReason","DEMO 测试人工核对侧重"));
    }
    List<ObjectNode> choices(JsonNode evaluated) {
        return List.of(confirmed(evaluated,"糖尿病","β细胞功能衰退型","常规管理"),
                confirmed(evaluated,"高血压","晨峰型","加强管理"));
    }
    String submit(Fixture f,List<ObjectNode> confirmed,int status) throws Exception {
        String key=UUID.randomUUID().toString();
        mvc.perform(post("/store/care/drafts/recommendations/generate").header("Authorization",TOKEN).header("X-Request-Id",key)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("archiveId",f.alias(),"confirmedManagement",confirmed))))
                .andExpect(status().is(status));
        return status==504?db.queryForObject("SELECT generation_job_id FROM legacy_recommendation_request WHERE request_key=?",String.class,key):null;
    }
    JsonNode input(String job) throws Exception {
        return json.readTree(db.queryForObject("SELECT model_input_json FROM plan_generation_job WHERE id=?",String.class,job));
    }
    @Test void bindsConfirmedSourcesCalculatesDocumentExampleAndKeepsHumanChoicesWhenAiOmitsThem() throws Exception {
        var f=fixture();var evaluation=evaluate(f);
        try(var stream=new org.springframework.core.io.ClassPathResource("contracts/legacy-management.openapi.json").getInputStream()) {
            var contract=json.readTree(stream);assertContract(evaluation,contract.path("components").path("schemas").path("Evaluated"),contract);
        }
        assertThat(evaluation.path("diseases").size()).isEqualTo(2);
        assertThat(evaluation.path("diseases").get(0).path("priority").asText()).isEqualTo("待确认");
        assertThat(evaluate(f).path("sourceHash")).isEqualTo(evaluation.path("sourceHash"));
        String job=submit(f,choices(evaluation),504);
        var input=input(job);
        assertThat(input.toString()).doesNotContain("侧重绑定虚构患者","demo-clinician");
        assertThat(input.path("managementContext").path("fusion").path("dimensionWeights")).isEqualTo(json.valueToTree(
                Map.of("medication",2500,"diet",1170,"exercise",1000,"routine",1170,"monitoring",3160,"tcm",500,"other",500)));
        when(client.generate(anyString(),anyString(),anyString())).thenReturn(new PlanAiClient.Result(
                AiPlanPolicyTest.candidate().replace("fact-1",f.routine()),"deepseek-flash","management-test","{}"));
        worker.process(job);
        var result=json.readTree(mvc.perform(get("/plan-generation-jobs/"+job).header("Authorization",TOKEN)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(result.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.path("candidate").path("managementFocuses").size()).isEqualTo(2);
        var adopted=postJson("/plan-generation-jobs/"+job+"/adoptions",Map.of("expectedRevision",result.path("baseRevision").asText()),200);
        assertThat(adopted.path("managementFocuses").size()).isEqualTo(2);
        assertThat(adopted.path("goalBasis").path("managementContext").path("sourceHash")).isEqualTo(evaluation.path("sourceHash"));
        assertThat(adopted.path("inputSnapshot").path("managementContext").path("confirmedBy").asText()).isEqualTo("demo-clinician");
        assertThat(adopted.path("status").asText()).isEqualTo("DRAFT");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM plan_management_focus WHERE plan_version_id=?",Integer.class,adopted.path("versionId").asText())).isEqualTo(2);
        assertThat(result.path("candidate").path("goalBasis").path("coordinationReport").path("coverage").size()).isEqualTo(2);
        assertThat(result.path("candidate").path("goalBasis").path("coordinationReport").path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        // Ordinary content edits must not let stale weights remain labeled as source-bound confirmation.
        mvc.perform(patch("/plan-versions/"+adopted.path("versionId").asText()).header("Authorization",TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":\"1\",\"managementFocuses\":[]}"))
                .andExpect(status().isBadRequest());
    }
    @Test void refusesTamperedEvaluationCrossPatientHashAndStaleFactsBeforeCreatingDrafts() throws Exception {
        var f=fixture();var ev=evaluate(f);
        var tampered=choices(ev);((ObjectNode)tampered.get(0).path("evaluation")).put("basis","篡改来源");
        submit(f,tampered,400);
        var foreign=choices(evaluate(fixture()));submit(f,foreign,409);
        var old=choices(evaluate(f));db.update("UPDATE health_fact SET value_json='{\"name\":\"糖尿病\",\"note\":\"资料更新\"}' WHERE id=?",f.diabetes());
        submit(f,old,409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM care_plan WHERE patient_id=?",Integer.class,f.patient())).isZero();
        verifyNoInteractions(client);
    }
    @Test void rejectsUnknownSubtypeInvalidWeightsDuplicateDiseaseAndMissingConfirmationReason() throws Exception {
        var f=fixture();
        var unknown=choices(evaluate(f));unknown.get(0).put("primarySubtype","凭空发明");submit(f,unknown,400);
        var reason=choices(evaluate(f));reason.get(0).put("adjustmentReason","");submit(f,reason,400);
        var weights=choices(evaluate(f));weights.get(0).set("weights",json.valueToTree(Map.of("用药",100)));submit(f,weights,400);
        var duplicate=choices(evaluate(f));submit(f,List.of(duplicate.get(0),duplicate.get(0)),400);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM care_plan WHERE patient_id=?",Integer.class,f.patient())).isZero();
    }
    @Test void missingDiseaseConfirmationNeverSilentlyRenormalizesAwayOtherDiseases() throws Exception {
        var f=fixture();var choices=choices(evaluate(f));String id=submit(f,List.of(choices.get(0)),504);
        var fusion=input(id).path("managementContext").path("fusion");
        assertThat(fusion.path("status").asText()).isEqualTo("PENDING");
        assertThat(fusion.path("dimensionWeights").isEmpty()).isTrue();
        assertThat(fusion.path("unconfirmedDiseases").toString()).contains("高血压");
        when(client.generate(anyString(),anyString(),anyString())).thenReturn(new PlanAiClient.Result(AiPlanPolicyTest.candidate().replace("fact-1",f.routine()),"deepseek-flash","partial","{}"));
        worker.process(id);
        var adopted=postJson("/plan-generation-jobs/"+id+"/adoptions",Map.of("expectedRevision","0"),200);
        var review=json.readTree(mvc.perform(get("/plan-versions/"+adopted.path("versionId").asText()+"/review-context").header("Authorization",TOKEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(review.path("data").path("issues").toString()).contains("MANAGEMENT_CONFIRMATION_PENDING");
    }
    @Test void selfReportIsNotDiagnosisAndEvaluationRequiresPatientAccess() throws Exception {
        var f=fixture();db.update("UPDATE health_fact SET confirmation_status='REPORTED' WHERE id=?",f.diabetes());
        assertThat(evaluate(f).path("diseases").size()).isEqualTo(1);
        mvc.perform(post("/store/care/drafts/basis/evaluate").contentType(MediaType.APPLICATION_JSON).content("{\"archiveId\":\""+f.alias()+"\"}"))
                .andExpect(status().isUnauthorized());
        db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=?",f.patient());
        postJson("/store/care/drafts/basis/evaluate",Map.of("archiveId",f.alias()),403);
    }
    @Test void nativeGenerationUsesTheSameBindingAndNewFactsPreventAdoption() throws Exception {
        var f=fixture();
        var ev=json.readTree(mvc.perform(get("/patients/"+f.patient()+"/management-basis").header("Authorization",TOKEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        var draft=postJson("/care-plans",Map.of("patientId",f.patient(),"expectedProfileRevision","1","title","原生来源测试",
                "startDate","2026-10-18","endDate","2026-10-24","zoneId","Asia/Shanghai"),201);
        var response=mvc.perform(post("/plan-versions/"+draft.path("versionId").asText()+"/generation-jobs").header("Authorization",TOKEN)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("expectedRevision","0","expectedProfileRevision","1","confirmedManagement",choices(ev)))))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        String job=json.readTree(response.getContentAsString()).path("data").path("id").asText();
        var raw=(ObjectNode)json.readTree(AiPlanPolicyTest.candidate().replace("fact-1",f.routine()));
        raw.set("managementFocuses",json.valueToTree(List.of(Map.of("diagnosisFactId",f.diabetes(),"primarySubtypeCode","模型擅自修改","priorityCode","重点管理"))));
        when(client.generate(anyString(),anyString(),anyString())).thenReturn(new PlanAiClient.Result(raw.toString(),"deepseek-flash","rewrite-test","{}"));
        worker.process(job);
        var candidate=json.readTree(db.queryForObject("SELECT candidate_json FROM plan_generation_job WHERE id=?",String.class,job));
        assertThat(candidate.path("managementFocuses").toString()).doesNotContain("模型擅自修改").contains("β细胞功能衰退型","晨峰型");
        fact(f.patient(),"E","support","{\"value\":\"新增照护条件\"}",null,null);
        postJson("/plan-generation-jobs/"+job+"/adoptions",Map.of("expectedRevision","0"),409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM plan_management_focus WHERE plan_version_id=?",Integer.class,draft.path("versionId").asText())).isZero();
    }
    @Test void conflictingDiagnosisAndAdjustedInvalidWeightsCannotEnterGeneration() throws Exception {
        var f=fixture();
        String second=diagnosis(f.patient(),"糖尿病");
        db.update("UPDATE health_fact SET conflict_group_key='management-test-conflict' WHERE id IN (?,?)",f.diabetes(),second);
        submit(f,choices(evaluate(f)),400);
        db.update("UPDATE health_fact SET conflict_group_key=NULL WHERE id IN (?,?)",f.diabetes(),second);
        var invalid=choices(evaluate(f));
        invalid.get(0).set("weights",json.valueToTree(Map.of("medication",25,"diet",15,"exercise",10,"routine",5,"monitoring",35,"tcm",5,"other",6)));
        submit(f,invalid,400);
        var valid=choices(evaluate(f));
        valid.get(0).set("weights",json.valueToTree(Map.of("用药",0,"饮食",20,"运动",20,"作息",20,"监测",30,"中医康复",5,"其他",5)));
        String job=submit(f,valid,504);
        assertThat(input(job).path("managementContext").path("focuses").toString()).contains("\"medication\":0");
    }
    void assertContract(JsonNode value,JsonNode schema,JsonNode doc) {
        if(schema.has("$ref")){assertContract(value,doc.at(schema.path("$ref").asText().substring(1)),doc);return;}
        switch(schema.path("type").asText()) {
            case "object" -> {
                assertThat(value.isObject()).isTrue();
                for(var field:value.properties()) {
                    var child=schema.path("properties").path(field.getKey());
                    if(child.isMissingNode())child=schema.path("additionalProperties");
                    assertThat(child.isObject()).as(field.getKey()).isTrue();assertContract(field.getValue(),child,doc);
                }
            }
            case "array" -> {assertThat(value.isArray()).isTrue();for(var item:value)assertContract(item,schema.path("items"),doc);}
            case "string" -> assertThat(value.isString()).isTrue();
            case "integer" -> assertThat(value.isIntegralNumber()).isTrue();
            default -> fail("Unsupported contract type: "+schema);
        }
    }
}
