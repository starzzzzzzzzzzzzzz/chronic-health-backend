package com.healthsystem;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class TaskGenerationApiTest extends TaskApiTestSupport {
    @Test void approvedPlanProducesOnlyItsExecutionDimensionAndKeepsSourceChain() throws Exception {
        var f=approved("routine",true);
        var b=bundle(f,"4");
        assertThat(b.path("sourcePlanVersionId").asText()).isEqualTo(f.version());
        assertThat(b.path("sourceReviewSubmissionId").asText()).isNotBlank();
        assertThat(b.path("sourceReviewDecisionId").asText()).isNotBlank();
        assertThat(b.path("generationMode").asText()).isEqualTo("RULE_BASED_DEMO");
        assertThat(b.path("rowVersion").asText()).isEqualTo("0");
        assertThat(b.path("groups").size()).isEqualTo(1);
        var g=b.path("groups").get(0);
        assertThat(g.path("dimensionCode").asText()).isEqualTo("routine");
        assertThat(g.path("sourcePlanItemIds").get(0).asText()).isEqualTo(f.item());
        assertThat(g.path("sourceFactIds").toString()).contains(f.fact());
        assertThat(g.path("feedbackSchema").path("required").isEmpty()).isTrue();
        assertThat(g.path("evaluationMode").asText()).isEqualTo("COMPLETION_ONLY");
        assertThat(b.path("issues").toString()).contains("SCHEDULE_REQUIRED");
        assertThat(getJson("/task-bundles/"+b.path("id").asText(),TOKEN,200).path("contentHash")).isEqualTo(b.path("contentHash"));
    }
    @Test void generationReadsNewExecutionFactsWithoutChangingApprovedPlan() throws Exception {
        var f=approved("routine",true);
        String added=fact(f.patient(),"E","execution_support","{\"value\":\"新增家庭协助\"}",null,null);
        db.update("UPDATE patient SET profile_revision=2 WHERE id=?",f.patient());
        var b=bundle(f,"4");
        assertThat(b.path("inputSnapshot").path("facts").toString()).contains(added);
        assertThat(b.path("nextCycleReference").toString()).contains(added);
        var plan=getJson("/plan-versions/"+f.version(),TOKEN,200);
        assertThat(plan.path("primaryGoal").asText()).isEqualTo("建立规律作息");
        assertThat(plan.path("inputSnapshot").path("facts").toString()).doesNotContain(added);
    }
    @Test void unapprovedStaleAndUnauthorizedSourcesAreRejected() throws Exception {
        var draft=draft("routine",true);grant(draft.patient(),"demo-clinician","TASK_EDIT");
        postJson("/plan-versions/"+draft.version()+"/task-bundles",TOKEN,Map.of("expectedRevision","1"),409);
        var f=approved("routine",true);
        postJson("/plan-versions/"+f.version()+"/task-bundles",TOKEN,Map.of("expectedRevision","3"),409);
        db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=? AND capability='TASK_EDIT'",f.patient());
        postJson("/plan-versions/"+f.version()+"/task-bundles",TOKEN,Map.of("expectedRevision","4"),403);
        mvc.perform(post("/plan-versions/"+f.version()+"/task-bundles").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void explanationOnlyPlanDoesNotInventSevenExecutionGroups() throws Exception {
        var f=approved("routine",false);var b=bundle(f,"3");
        assertThat(b.path("groups").isEmpty()).isTrue();
        assertThat(b.path("issues").toString()).contains("NO_EXECUTION_ITEMS");
    }
    @Test void patchCannotReplaceSourceGoalOrTemplateParameters() throws Exception {
        var f=approved("routine",true);var b=bundle(f,"4");String id=b.path("id").asText();
        mvc.perform(patch("/task-bundles/"+id).header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":\"0\",\"sourcePlanVersionId\":\"forged\"}"))
                .andExpect(status().isBadRequest());
        assertThat(getJson("/task-bundles/"+id,TOKEN,200).path("rowVersion").asText()).isEqualTo("0");
    }
    @Test void editingExecutionConfirmationAdvancesParentAndRejectsStaleRevision() throws Exception {
        var f=approved("routine",true);var b=bundle(f,"4");String id=b.path("id").asText();
        var body=Map.of("expectedRevision","0","groups",List.of(Map.of("id",b.path("groups").get(0).path("id").asText(),"executionConfirmed",true,"confirmationNote","已核对执行动作与目标")));
        var response=mvc.perform(patch("/task-bundles/"+id).header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse();
        var edited=json.readTree(response.getContentAsString()).path("data");
        assertThat(edited.path("rowVersion").asText()).isEqualTo("1");
        assertThat(edited.path("contentRevision").asText()).isEqualTo("1");
        assertThat(edited.path("issues").toString()).doesNotContain("ACTION_CONFIRMATION_REQUIRED");
        mvc.perform(patch("/task-bundles/"+id).header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }
}
