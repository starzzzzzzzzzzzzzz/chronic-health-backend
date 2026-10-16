package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class PlanVersionApiTest extends ReviewApiTestSupport {
    @Test void approvedPlanCreatesIndependentDraftWithNewChildIdsAndNoInheritedConfirmations() throws Exception {
        var f=draft("routine",true);confirm(f,"1");var s=submit(f,"2");
        postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),201);
        var original=getJson("/plan-versions/"+f.version(),TOKEN,200);
        var created=postJson("/care-plans/"+f.plan()+"/versions",TOKEN,Map.of("sourceVersionId",f.version(),"expectedRevision","4","changeReason","医生主动调整周期目标"),201);
        String id=created.path("versionId").asText();
        assertThat(created.path("versionNo").intValue()).isEqualTo(2);
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("rowVersion").asText()).isEqualTo("0");
        assertThat(created.path("contentRevision").asText()).isEqualTo("0");
        assertThat(created.path("items").get(0).path("id").asText()).isNotEqualTo(f.item());
        assertThat(created.path("modules").get(0).path("id")).isNotEqualTo(original.path("modules").get(0).path("id"));
        assertThat(created.path("dimensions").get(0).path("id")).isNotEqualTo(original.path("dimensions").get(0).path("id"));
        assertThat(created.path("items").get(0).path("basis").get(0).path("factId").asText()).isEqualTo(f.fact());
        assertThat(created.path("inputSnapshotId")).isEqualTo(original.path("inputSnapshotId"));
        assertThat(db.queryForObject("SELECT based_on_version_id FROM care_plan_version WHERE id=?",String.class,id)).isEqualTo(f.version());
        assertThat(db.queryForObject("SELECT current_version_id FROM care_plan WHERE id=?",String.class,f.plan())).isEqualTo(id);
        var context=getJson("/plan-versions/"+id+"/review-context",TOKEN,200);
        assertThat(context.path("submissions").size()).isZero();
        assertThat(context.path("confirmations").path("history").size()).isZero();
        assertThat(context.path("confirmations").path("conditions").get(0).path("status").asText()).isEqualTo("MISSING");
        postJson("/care-plans/"+f.plan()+"/versions",TOKEN,Map.of("sourceVersionId",f.version(),"expectedRevision","4","changeReason","重复请求"),409);
        assertThat(getJson("/review-submissions/"+s.path("id").asText(),REVIEWER,200).path("snapshot")).isEqualTo(s.path("snapshot"));
        patchJson(id,Map.of("expectedRevision","0","primaryGoal","新的周期管理目标"),200);
        assertThat(getJson("/plan-versions/"+f.version(),TOKEN,200).path("primaryGoal")).isEqualTo(original.path("primaryGoal"));
    }
    @Test void refusesUnapprovedOrForeignSourcesAndReviewerHasNoEditCapability() throws Exception {
        var f=draft("routine",false);
        var body=Map.of("sourceVersionId",f.version(),"expectedRevision","1","changeReason","请求调整");
        postJson("/care-plans/"+f.plan()+"/versions",TOKEN,body,409);
        postJson("/care-plans/"+f.plan()+"/versions",REVIEWER,body,403);
        postJson("/care-plans/"+UUID.randomUUID()+"/versions",TOKEN,body,404);
        postJson("/care-plans/"+f.plan()+"/versions",TOKEN,Map.of("sourceVersionId",UUID.randomUUID().toString(),"expectedRevision","1","changeReason","错误来源"),404);
        db.update("UPDATE patient SET data_mode='REAL' WHERE id=?",f.patient());
        postJson("/care-plans/"+f.plan()+"/versions",TOKEN,body,404);
    }
}
