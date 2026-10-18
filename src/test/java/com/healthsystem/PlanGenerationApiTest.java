package com.healthsystem;

import com.healthsystem.service.PlanAiClient;
import com.healthsystem.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"health.demo.enabled=true","health.demo.token=development-test-token-only",
    "health.ai.enabled=true","health.ai.api-key=test-placeholder-never-sent","health.ai.worker-enabled=false"})
@Transactional
class PlanGenerationApiTest extends DatabaseApiTestSupport {
    @MockitoBean PlanAiClient client;
    @Autowired PlanGenerationWorker worker;
    @Autowired PlanGenerationService generations;
    record Fixture(String patient,String fact,String version) {}
    Fixture fixture() throws Exception {
        var p=patient("demo-org","DEMO","AI测试虚构患者",true);
        var f=fact(p,"B","routine","{\"value\":\"晚睡\"}",null,null);
        var body="""
            {"patientId":"%s","expectedProfileRevision":"1","title":"AI测试方案","startDate":"2026-10-18","endDate":"2026-10-24","zoneId":"Asia/Shanghai"}
            """.formatted(p);
        var response=mvc.perform(post("/care-plans").header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Fixture(p,f,json.readTree(response).path("data").path("versionId").asText());
    }
    JsonNode submit(Fixture f,String key,String extra,int status) throws Exception {
        String body="{\"expectedRevision\":\"0\",\"expectedProfileRevision\":\"1\""+extra+"}";
        String result=mvc.perform(post("/plan-versions/"+f.version()+"/generation-jobs").header("Authorization",TOKEN).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return json.readTree(result).path("data");
    }
    void answer(Fixture f) {doReturn(new PlanAiClient.Result(AiPlanPolicyTest.candidate().replace("fact-1",f.fact()),"deepseek-flash","test-response","{\"total_tokens\":100}")).when(client).generate(anyString(),anyString(),anyString());}
    JsonNode getJob(String id) throws Exception {
        String response=mvc.perform(get("/plan-generation-jobs/"+id).header("Authorization",TOKEN)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("test-placeholder-never-sent","rawOutput","systemPrompt");return json.readTree(response).path("data");
    }
    JsonNode adopt(String job,int status) throws Exception {
        return json.readTree(mvc.perform(post("/plan-generation-jobs/"+job+"/adoptions").header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":\"0\"}")).andExpect(status().is(status)).andReturn().getResponse().getContentAsString()).path("data");
    }
    @Test void freezesLatestInputGeneratesSeparatelyAndAdoptsIntoEditableDraft() throws Exception {
        var f=fixture();answer(f);
        var j=submit(f,"once","",202);String id=j.path("id").asText();
        assertThat(submit(f,"once","",202).path("id").asText()).isEqualTo(id);
        submit(f,"second","",409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM health_input_snapshot WHERE patient_id=?",Integer.class,f.patient())).isEqualTo(2);
        worker.process(id);worker.process(id);verify(client,times(1)).generate(anyString(),anyString(),anyString());
        assertThat(getJob(id).path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(getJob(id).path("usage").path("providerModel").asText()).isEqualTo("deepseek-flash");
        assertThat(db.queryForObject("SELECT row_version FROM care_plan_version WHERE id=?",Long.class,f.version())).isZero();
        var saved=adopt(id,200);
        assertThat(saved.path("status").asText()).isEqualTo("DRAFT");assertThat(saved.path("rowVersion").asText()).isEqualTo("1");
        assertThat(saved.path("inputSnapshotId").asText()).isEqualTo(j.path("inputSnapshotId").asText());
        assertThat(saved.path("goalBasis").path("generationJobId").asText()).isEqualTo(id);
        assertThat(saved.path("items").size()).isEqualTo(1);
        assertThat(adopt(id,200).path("rowVersion").asText()).isEqualTo("1");
        assertThat(getJob(id).path("status").asText()).isEqualTo("APPLIED");
        mvc.perform(get("/plan-versions/"+f.version()+"/generation-jobs").header("Authorization",TOKEN)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }
    @Test void manualEditDuringGenerationCannotBeOverwritten() throws Exception {
        var f=fixture();answer(f);String id=submit(f,"edit","",202).path("id").asText();worker.process(id);
        mvc.perform(patch("/plan-versions/"+f.version()).header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":\"0\",\"title\":\"医生手工修改\"}")).andExpect(status().isOk());
        adopt(id,409);assertThat(db.queryForObject("SELECT title FROM care_plan_version WHERE id=?",String.class,f.version())).isEqualTo("医生手工修改");
    }
    @Test void hallucinatedFactIsRejectedWithoutWritingDraft() throws Exception {
        var f=fixture();when(client.generate(anyString(),anyString(),anyString())).thenReturn(new PlanAiClient.Result(AiPlanPolicyTest.candidate(),"deepseek-flash","bad","{}"));
        String id=submit(f,"bad","",202).path("id").asText();worker.process(id);
        assertThat(getJob(id).path("status").asText()).isEqualTo("REJECTED");adopt(id,409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM care_plan_item WHERE plan_version_id=?",Integer.class,f.version())).isZero();
    }
    @Test void providerFailureHasSafeCodeAndRetryCreatesAnotherJob() throws Exception {
        var f=fixture();when(client.generate(anyString(),anyString(),anyString())).thenThrow(new DeepSeekPlanClient.AiFailure("AI_TIMEOUT"));
        String id=submit(f,"timeout","",202).path("id").asText();worker.process(id);
        assertThat(getJob(id).path("errorCode").asText()).isEqualTo("AI_TIMEOUT");
        assertThat(submit(f,"timeout","",202).path("id").asText()).isEqualTo(id);
        String next=submit(f,"retry",",\"retryOfJobId\":\""+id+"\"",202).path("id").asText();assertThat(next).isNotEqualTo(id);
        answer(f);worker.process(next);assertThat(getJob(next).path("status").asText()).isEqualTo("SUCCEEDED");
    }
    @Test void authorizationIsRecheckedBeforeSendingToProvider() throws Exception {
        var f=fixture();String id=submit(f,"revoke","",202).path("id").asText();
        db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=?",f.patient());
        worker.process(id);verifyNoInteractions(client);
        assertThat(db.queryForObject("SELECT error_code FROM plan_generation_job WHERE id=?",String.class,id)).isEqualTo("AI_ACCESS_REVOKED");
        mvc.perform(get("/plan-generation-jobs/"+id).header("Authorization",TOKEN)).andExpect(status().isForbidden());
    }
    @Test void approvedVersionCannotGenerateAndCrossOrgCannotReadJob() throws Exception {
        var f=fixture();String id=submit(f,"scope","",202).path("id").asText();
        assertThatThrownBy(()->generations.get(new com.healthsystem.common.security.BusinessPrincipal("other-org","demo-clinician","DEMO"),id))
            .isInstanceOf(com.healthsystem.exception.BusinessException.class).hasMessage("Generation job not found");
        mvc.perform(get("/plan-generation-jobs/"+UUID.randomUUID()).header("Authorization",TOKEN)).andExpect(status().isNotFound());
        mvc.perform(get("/plan-generation-jobs/"+id)).andExpect(status().isUnauthorized());
        db.update("UPDATE plan_generation_job SET status='FAILED' WHERE id=?",id);
        db.update("UPDATE care_plan_version SET status='APPROVED' WHERE id=?",f.version());
        submit(f,"approved","",409);
    }
    @Test void queuedJobFromAnOlderRuleVersionRequiresExplicitRetryWithoutProviderCall() throws Exception {
        var f=fixture();String id=submit(f,"old-rules","",202).path("id").asText();
        db.update("UPDATE plan_generation_job SET rule_version='retired-rules' WHERE id=?",id);
        worker.process(id);verifyNoInteractions(client);
        assertThat(getJob(id).path("errorCode").asText()).isEqualTo("AI_RULE_VERSION_CHANGED");
        assertThat(db.queryForObject("SELECT row_version FROM care_plan_version WHERE id=?",Long.class,f.version())).isZero();
    }
    @Test void rejectsChangedRequestWithSameIdempotencyKeyAndOutdatedProfile() throws Exception {
        var f=fixture();submit(f,"same","",202);
        submit(f,"same",",\"retryOfJobId\":\""+UUID.randomUUID()+"\"",409);
        db.update("UPDATE patient SET profile_revision=2 WHERE id=?",f.patient());
        submit(f,"outdated-profile","",409);
        verifyNoInteractions(client);
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void adoptingInvalidCandidateRollsBackSnapshotSwitch() throws Exception {
        var f=fixture();
        try {
            answer(f);
            String before=db.queryForObject("SELECT input_snapshot_id FROM care_plan_version WHERE id=?",String.class,f.version());
            String id=submit(f,"rollback-"+UUID.randomUUID(),"",202).path("id").asText();worker.process(id);
            db.update("UPDATE plan_generation_job SET candidate_json=JSON_SET(candidate_json,'$.goalBasis.factIds',JSON_ARRAY('foreign')) WHERE id=?",id);
            adopt(id,400);
            assertThat(db.queryForObject("SELECT input_snapshot_id FROM care_plan_version WHERE id=?",String.class,f.version())).isEqualTo(before);
            assertThat(getJob(id).path("status").asText()).isEqualTo("SUCCEEDED");
        } finally {
            db.update("DELETE FROM plan_generation_job WHERE plan_version_id=?",f.version());
            db.update("UPDATE care_plan SET current_version_id=NULL WHERE patient_id=?",f.patient());
            db.update("DELETE FROM audit_log WHERE target_id=?",f.version());
            db.update("DELETE FROM care_plan_version WHERE id=?",f.version());
            db.update("DELETE FROM care_plan WHERE patient_id=?",f.patient());
            db.update("DELETE FROM health_input_snapshot WHERE patient_id=?",f.patient());
            db.update("DELETE FROM health_fact WHERE patient_id=?",f.patient());
            db.update("DELETE FROM patient_access_grant WHERE patient_id=?",f.patient());
            db.update("DELETE FROM patient WHERE id=?",f.patient());
        }
    }
    @Test void generationUsesNewFactsButDoesNotChangeAnExistingSnapshotUntilAdopted() throws Exception {
        var f=fixture();
        String latest=fact(f.patient(),"B","routine","{\"value\":\"已经规律起床，想记录入睡时间\"}",f.fact(),null);
        answer(new Fixture(f.patient(),latest,f.version()));
        String id=submit(f,"new-input","",202).path("id").asText();worker.process(id);
        var saved=adopt(id,200);
        assertThat(saved.path("inputSnapshot").path("facts").toString()).contains(latest).doesNotContain(f.fact());
    }

}
