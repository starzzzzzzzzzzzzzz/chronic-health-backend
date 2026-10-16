package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class PlanReviewApiTest extends ReviewApiTestSupport {
    String context(Fixture f){return "/plan-versions/"+f.version()+"/review-context";}
    @Test void submissionLocksFullSnapshotAndIndependentApprovalFreezesVersion() throws Exception {
        var f=draft("routine",true);confirm(f,"1");
        var submitted=submit(f,"2");String id=submitted.path("id").asText();
        assertThat(submitted.path("status").asText()).isEqualTo("PENDING");
        assertThat(submitted.path("targetRevision").asText()).isEqualTo("1");
        assertThat(submitted.path("snapshot").path("plan").path("inputSnapshot").path("facts").size()).isEqualTo(1);
        assertThat(submitted.path("snapshot").path("confirmations").size()).isEqualTo(1);
        var waiting=getJson("/plan-versions/"+f.version(),TOKEN,200);
        assertThat(waiting.path("status").asText()).isEqualTo("IN_REVIEW");
        assertThat(waiting.path("rowVersion").asText()).isEqualTo("3");
        assertThat(waiting.path("contentRevision").asText()).isEqualTo("1");
        patchJson(f.version(),Map.of("expectedRevision","3","title","待审不能编辑"),409);
        postJson(conditions(f),TOKEN,Map.of("expectedRevision","3","entries",List.of(executionEntry("execution."+f.item()))),409);
        var approved=postJson("/review-submissions/"+id+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE","comment","已核对目标、依据、禁忌与执行主体；虚构联调"),201);
        assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.path("decision").path("reviewerId").asText()).isEqualTo("demo-reviewer");
        assertThat(approved.path("contentHash").asText()).isEqualTo(submitted.path("contentHash").asText());
        assertThat(approved.path("snapshot")).isEqualTo(submitted.path("snapshot"));
        assertThat(getJson("/plan-versions/"+f.version(),TOKEN,200).path("status").asText()).isEqualTo("APPROVED");
        patchJson(f.version(),Map.of("expectedRevision","4","primaryGoal","擅改已审目标"),409);
        postJson("/review-submissions/"+id+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","RETURN","comment","重复操作"),409);
    }
    @Test void returnKeepsOpinionAndResubmissionCreatesNewImmutableRecord() throws Exception {
        var f=draft("routine",false);var first=submit(f,"1");String id=first.path("id").asText();
        postJson("/review-submissions/"+id+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","RETURN"),400);
        postJson("/review-submissions/"+id+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","RETURN","comment","请补充目标说明"),201);
        patchJson(f.version(),Map.of("expectedRevision","3","title","补充后的方案"),200);
        var second=submit(f,"4");
        assertThat(second.path("id").asText()).isNotEqualTo(id);
        var old=getJson("/review-submissions/"+id,TOKEN,200);
        assertThat(old.path("status").asText()).isEqualTo("RETURNED");
        assertThat(old.path("decision").path("comment").asText()).isEqualTo("请补充目标说明");
        assertThat(old.path("snapshot")).isEqualTo(first.path("snapshot"));
        assertThat(getJson(context(f),REVIEWER,200).path("submissions").size()).isEqualTo(2);
    }
    @Test void withdrawalIsTerminalDoesNotForgeDecisionAndAllowsNewSubmission() throws Exception {
        var f=draft("routine",false);var first=submit(f,"1");String id=first.path("id").asText();
        postJson("/review-submissions/"+id+"/withdrawals",REVIEWER,Map.of("expectedRevision","0","reason","修改内容"),403);
        var withdrawn=postJson("/review-submissions/"+id+"/withdrawals",TOKEN,Map.of("expectedRevision","0","reason","补充资料后重提"),201);
        assertThat(withdrawn.path("status").asText()).isEqualTo("WITHDRAWN");
        assertThat(withdrawn.path("decision").isNull()).isTrue();
        assertThat(withdrawn.path("closedReason").asText()).isEqualTo("补充资料后重提");
        postJson("/review-submissions/"+id+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),409);
        assertThat(submit(f,"3").path("id").asText()).isNotEqualTo(id);
    }
    @Test void missingGoalBasisItemsAndStaleConditionsCannotPassSubmissionValidation() throws Exception {
        var f=draft("routine",true);
        var initial=getJson(context(f),TOKEN,200);
        assertThat(initial.path("issues").toString()).contains("CONDITION_NOT_CONFIRMED");
        postJson(submissions(f),TOKEN,Map.of("expectedRevision","1"),400);
        confirm(f,"1");patchJson(f.version(),Map.of("expectedRevision","2","primaryGoal","新的目标"),200);
        postJson(submissions(f),TOKEN,Map.of("expectedRevision","3"),400);
        confirm(f,"3");
        var clear=new HashMap<String,Object>();clear.put("expectedRevision","4");clear.put("primaryGoal",null);clear.put("goalBasis",null);
        patchJson(f.version(),clear,200);
        var invalid=getJson(context(f),TOKEN,200);
        assertThat(invalid.path("issues").toString()).contains("PRIMARY_GOAL_REQUIRED","GOAL_BASIS_REQUIRED");
        postJson(submissions(f),TOKEN,Map.of("expectedRevision","5"),400);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM review_submission WHERE plan_version_id=?",Integer.class,f.version())).isZero();
    }
    @Test void doesNotRequireAllSevenDimensionsButBlocksUnreviewedTcmExecution() throws Exception {
        var simple=draft("routine",false);assertThat(submit(simple,"1").path("status").asText()).isEqualTo("PENDING");
        var tcm=draft("tcm",true);confirm(tcm,"1");
        assertThat(getJson(context(tcm),TOKEN,200).path("issues").toString()).contains("TCM_TEMPLATE_REVIEW_REQUIRED");
        postJson(submissions(tcm),TOKEN,Map.of("expectedRevision","2"),400);
    }
    @Test void independentReviewerCannotEditAndSubmitterCannotSelfApproveEvenWithReviewGrant() throws Exception {
        var f=draft("routine",false);var s=submit(f,"1");String id=s.path("id").asText();
        postJson("/review-submissions/"+id+"/decisions",TOKEN,Map.of("expectedRevision","0","decision","APPROVE"),403);
        grant(f.patient(),"demo-clinician","PLAN_REVIEW");
        postJson("/review-submissions/"+id+"/decisions",TOKEN,Map.of("expectedRevision","0","decision","APPROVE"),403);
        postJson(submissions(f),REVIEWER,Map.of("expectedRevision","2"),403);
        db.update("UPDATE patient_access_grant SET valid_until=UTC_TIMESTAMP(6) WHERE patient_id=? AND subject_id='demo-reviewer' AND capability='PLAN_REVIEW'",f.patient());
        postJson("/review-submissions/"+id+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),403);
        db.update("UPDATE patient SET data_mode='REAL' WHERE id=?",f.patient());
        getJson("/review-submissions/"+id,REVIEWER,404);
    }
    @Test void profileUpdatesDoNotSilentlyChangeSubmissionOrInvalidateOrdinaryReview() throws Exception {
        var f=draft("routine",false);var s=submit(f,"1");
        String added=fact(f.patient(),"E","environment","{}",null,null);
        db.update("UPDATE patient SET profile_revision=2 WHERE id=?",f.patient());
        var stored=getJson("/review-submissions/"+s.path("id").asText(),REVIEWER,200);
        assertThat(stored.path("snapshot").toString()).doesNotContain(added);
        postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),201);
    }
}
