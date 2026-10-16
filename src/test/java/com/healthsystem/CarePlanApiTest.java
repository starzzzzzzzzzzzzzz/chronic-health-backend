package com.healthsystem;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class CarePlanApiTest extends DatabaseApiTestSupport {
    String createBody(String patient) {return "{\"patientId\":\""+patient+"\",\"expectedProfileRevision\":\"1\",\"title\":\"虚构患者周期方案\",\"startDate\":\"2026-10-17\",\"endDate\":\"2026-10-23\",\"zoneId\":\"Asia/Shanghai\"}";}
    JsonNode create(String patient) throws Exception {
        var response=mvc.perform(post("/care-plans").header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(createBody(patient)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(201)).andExpect(header().exists("Location")).andExpect(jsonPath("$.data.rowVersion").value("0")).andReturn().getResponse();
        var data=json.readTree(response.getContentAsString()).path("data");
        assertThat(response.getHeader("Location")).isEqualTo("/ncd-ai/plan-versions/"+data.path("versionId").asText());return data;
    }
    JsonNode patch(String version,String body,int expected) throws Exception {
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/plan-versions/"+version).header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected)).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("data");
    }
    String filledPatch(String revision,String fact) {
        return """
        {"expectedRevision":"%s","primaryGoal":"建立规律作息",
         "modules":[{"moduleCode":"B0","selectionReason":"演示作息目标","factIds":["%s"]}],
         "dimensions":[{"dimensionCode":"routine","decision":"ENABLED","sortOrder":0}],
         "items":[{"dimensionCode":"routine","title":"作息记录","content":"示例候选，待审核",
           "executionGoal":true,"actorType":"PATIENT","isSelected":true,"sortOrder":0,
           "basis":[{"moduleCode":"B0","factId":"%s"}]}]}
        """.formatted(revision,fact,fact);
    }
    @Test void createsEditsAndReopensDraftAndSupportsCollectionPatchSemantics() throws Exception {
        String patient=patient("demo-org","DEMO","草稿虚构患者",true);
        String fact=fact(patient,"B","routine","{\"valueType\":\"TEXT\",\"value\":\"晚睡\"}",null,null);
        var draft=create(patient);String id=draft.path("versionId").asText();
        var saved=patch(id,filledPatch("0",fact),200);
        assertThat(saved.path("rowVersion").asText()).isEqualTo("1");
        assertThat(saved.path("contentRevision").asText()).isEqualTo("1");
        assertThat(saved.path("items").size()).isEqualTo(1);
        var renamed=patch(id,"{\"expectedRevision\":\"1\",\"title\":\"新的草稿名称\"}",200);
        assertThat(renamed.path("items").get(0).path("id").asText()).isEqualTo(saved.path("items").get(0).path("id").asText());
        mvc.perform(get("/plan-versions/"+id).header("Authorization",TOKEN)).andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("新的草稿名称")).andExpect(jsonPath("$.data.primaryGoal").value("建立规律作息"));
        var cleared=patch(id,"{\"expectedRevision\":\"2\",\"modules\":[],\"dimensions\":[],\"items\":[],\"primaryGoal\":null}",200);
        assertThat(cleared.path("items").size()).isZero();assertThat(cleared.path("primaryGoal").isNull()).isTrue();
    }
    @Test void staleOrForeignSourcesCannotPartiallyWriteAndReviewedDraftCannotChange() throws Exception {
        String patient=patient("demo-org","DEMO","并发控制虚构患者",true);
        var draft=create(patient);String id=draft.path("versionId").asText();
        patch(id,"{\"expectedRevision\":\"0\",\"title\":\"先保存\"}",200);
        patch(id,"{\"expectedRevision\":\"0\",\"title\":\"旧保存\"}",409);
        String other=patient("demo-org","DEMO","另一位虚构患者",true);
        String foreign=fact(other,"B","routine","{}",null,null);
        int count=db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE target_id=?",Integer.class,id);
        patch(id,filledPatch("1",foreign),400);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE target_id=?",Integer.class,id)).isEqualTo(count);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM care_plan_module WHERE plan_version_id=?",Integer.class,id)).isZero();
        db.update("UPDATE care_plan_version SET status='APPROVED' WHERE id=?",id);
        assertThat(db.queryForObject("SELECT status FROM care_plan_version WHERE id=?",String.class,id)).isEqualTo("APPROVED");
        patch(id,"{\"expectedRevision\":\"1\",\"title\":\"不能改\"}",409);
    }
    @Test void snapshotsRemainFrozenAndChangedProfileBlocksStaleCreate() throws Exception {
        String patient=patient("demo-org","DEMO","快照虚构患者",true);
        String old=fact(patient,"B","routine","{\"valueType\":\"TEXT\",\"value\":\"旧事实\"}",null,null);
        var draft=create(patient);String id=draft.path("versionId").asText();
        fact(patient,"B","routine","{\"valueType\":\"TEXT\",\"value\":\"新事实\"}",old,null);
        db.update("UPDATE patient SET profile_revision=2 WHERE id=?",patient);
        var response=mvc.perform(get("/plan-versions/"+id).header("Authorization",TOKEN)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).contains("旧事实").doesNotContain("新事实");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM health_input_snapshot WHERE patient_id=?",Integer.class,patient)).isEqualTo(1);
        mvc.perform(post("/care-plans").header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(createBody(patient))).andExpect(status().isConflict());
    }
    @Test void rejectsInvalidDatesZonesWeightsForeignChildrenAndCrossMode() throws Exception {
        String patient=patient("demo-org","DEMO","校验虚构患者",true);
        for(String invalid:new String[]{createBody(patient).replace("2026-10-23","2026-10-01"),createBody(patient).replace("Asia/Shanghai","No/SuchZone"),createBody(patient).replace("虚构患者周期方案"," ")})
            mvc.perform(post("/care-plans").header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        var draft=create(patient);String id=draft.path("versionId").asText();
        patch(id,"{\"expectedRevision\":\"0\",\"items\":[{\"id\":\"foreign-id\",\"dimensionCode\":\"routine\"}]}",400);
        patch(id,"{\"expectedRevision\":\"0\",\"managementFocuses\":[{\"diagnosisFactId\":\"not-a-fact\",\"dimensionWeights\":{\"routine\":9000}}]}",400);
        patch(id,"{\"expectedRevision\":\"0\",\"dimensions\":[{\"dimensionCode\":\"invalid\",\"decision\":\"ENABLED\"}]}",400);
        String real=patient("demo-org","REAL","模式隔离占位",true);
        mvc.perform(post("/care-plans").header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(createBody(real))).andExpect(status().isNotFound());
    }
    @Test void changingOnlyRevisionReasonPersistsWithoutChangingContentRevision() throws Exception {
        String patient=patient("demo-org","DEMO","修订说明虚构患者",true);
        var draft=create(patient);String id=draft.path("versionId").asText();
        var result=patch(id,"{\"expectedRevision\":\"0\",\"changeReason\":\"补充编辑说明\"}",200);
        assertThat(result.path("rowVersion").asText()).isEqualTo("1");
        assertThat(result.path("contentRevision").asText()).isEqualTo("0");
        mvc.perform(get("/plan-versions/"+id).header("Authorization",TOKEN)).andExpect(status().isOk()).andExpect(jsonPath("$.data.changeReason").value("补充编辑说明"));
    }
    @Test void storesConfirmedDiseaseFocusAndPreservesHashOnEquivalentSave() throws Exception {
        String patient=patient("demo-org","DEMO","侧重测试虚构患者",true);
        String fact=fact(patient,"H","diagnosis.test","{\"valueType\":\"CODE\",\"value\":\"TEST_ONLY\"}",null,null);
        db.update("UPDATE health_fact SET confirmation_status='CONFIRMED_PROFESSIONAL',source_type='PROFESSIONAL_RECORD',confirmed_by='test-doctor',confirmed_at=UTC_TIMESTAMP(6) WHERE id=?",fact);
        var draft=create(patient);String id=draft.path("versionId").asText();
        var body="{\"expectedRevision\":\"0\",\"managementFocuses\":[{\"diagnosisFactId\":\""+fact+"\",\"dimensionWeights\":{\"medication\":0,\"diet\":0,\"exercise\":0,\"routine\":10000,\"monitoring\":0,\"tcm\":0,\"other\":0},\"contributionBps\":10000}]}";
        var saved=patch(id,body,200);
        assertThat(saved.path("managementFocuses").get(0).path("contributionBps").intValue()).isEqualTo(10000);
        var repeat=patch(id,body.replace("\"0\"","\"1\""),200);
        assertThat(repeat.path("contentHash").asText()).isEqualTo(saved.path("contentHash").asText());
        assertThat(repeat.path("rowVersion").asText()).isEqualTo("1");
    }
    @Test void malformedNullBasisEntryIsBadRequestAndLeavesDraftUnchanged() throws Exception {
        String patient=patient("demo-org","DEMO","空依据测试虚构患者",true);
        var draft=create(patient);String id=draft.path("versionId").asText();
        String body="{\"expectedRevision\":\"0\",\"dimensions\":[{\"dimensionCode\":\"routine\",\"decision\":\"ENABLED\"}],\"items\":[{\"dimensionCode\":\"routine\",\"title\":\"x\",\"content\":\"x\",\"actorType\":\"PATIENT\",\"isSelected\":false,\"basis\":[null]}]}";
        patch(id,body,400);
        assertThat(db.queryForObject("SELECT row_version FROM care_plan_version WHERE id=?",Long.class,id)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM care_plan_dimension WHERE plan_version_id=?",Integer.class,id)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE target_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentRequestsWithSameRevisionHaveExactlyOneWinner() throws Exception {
        String patient=patient("demo-org","DEMO","真实事务并发虚构患者",true);
        String version=null;
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            version=create(patient).path("versionId").asText();final String id=version;
            CountDownLatch start=new CountDownLatch(1);
            List<Future<Integer>> futures=new ArrayList<>();
            for(String title:List.of("并发甲","并发乙")) futures.add(executor.submit(()->{
                start.await();return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/plan-versions/"+id).header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":\"0\",\"title\":\""+title+"\"}")).andReturn().getResponse().getStatus();
            }));
            start.countDown();
            assertThat(List.of(futures.get(0).get(10,TimeUnit.SECONDS),futures.get(1).get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(db.queryForObject("SELECT row_version FROM care_plan_version WHERE id=?",Long.class,version)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            if(version!=null){db.update("UPDATE care_plan SET current_version_id=NULL WHERE patient_id=?",patient);db.update("DELETE FROM audit_log WHERE target_id=?",version);db.update("DELETE FROM care_plan_version WHERE id=?",version);}
            db.update("DELETE FROM care_plan WHERE patient_id=?",patient);db.update("DELETE FROM health_input_snapshot WHERE patient_id=?",patient);db.update("DELETE FROM patient_access_grant WHERE patient_id=?",patient);db.update("DELETE FROM patient WHERE id=?",patient);
        }
    }
}
