package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@Transactional
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class TaskReviewApiTest extends TaskApiTestSupport {
    String submitRoute(String bundle){return "/task-bundles/"+bundle+"/review-submissions";}
    @Test void taskReviewHasItsOwnStageAndCannotBeSelfApprovedOrEditedWhilePending() throws Exception {
        var b=readyBundle();var p=preview(b);String id=b.path("id").asText();
        grant(b.path("patientId").asText(),"demo-clinician","TASK_REVIEW");
        var s=postJson(submitRoute(id),TOKEN,Map.of("expectedRevision","2","schedulePreviewId",p.path("id").asText()),201);
        assertThat(s.path("stage").asText()).isEqualTo("TASK");assertThat(s.path("taskBundleId").asText()).isEqualTo(id);
        postJson("/review-submissions/"+s.path("id").asText()+"/decisions",TOKEN,Map.of("expectedRevision","0","decision","APPROVE"),403);
        editBundle(id,Map.of("expectedRevision","3","groups",List.of()),409);
        var result=postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),201);
        assertThat(result.path("status").asText()).isEqualTo("APPROVED");
        assertThat(getJson("/task-bundles/"+id,TOKEN,200).path("status").asText()).isEqualTo("APPROVED");
        postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),409);
    }
    @Test void editingInvalidatesAnOldPreviewAndAnOldTaskApprovalDoesNotAuthorizeANewBundle() throws Exception {
        var b=readyBundle();var p=preview(b);String id=b.path("id").asText();
        b=editBundle(id,Map.of("expectedRevision","2","groups",List.of(Map.of("id",b.path("groups").get(0).path("id").asText(),"displayNote","调整提醒说明"))),200);
        assertThat(b.path("latestPreviewId").isNull()).isTrue();
        postJson(submitRoute(id),TOKEN,Map.of("expectedRevision","3","schedulePreviewId",p.path("id").asText()),409);
        p=preview(b);
        var s=postJson(submitRoute(id),TOKEN,Map.of("expectedRevision","4","schedulePreviewId",p.path("id").asText()),201);
        postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),201);
        var clone=postJson("/task-bundles/"+id+"/versions",TOKEN,Map.of("expectedRevision","6","changeReason","新周期编排调整"),201);
        assertThat(clone.path("status").asText()).isEqualTo("DRAFT");assertThat(clone.path("basedOnBundleId").asText()).isEqualTo(id);
        assertThat(clone.path("groups").get(0).path("id")).isNotEqualTo(b.path("groups").get(0).path("id"));
        assertThat(clone.path("latestPreviewId").isNull()).isTrue();
    }
    @Test void returningOrWithdrawingKeepsHistoryAndAllowsANewSubmission() throws Exception {
        var b=readyBundle();var p=preview(b);String id=b.path("id").asText();
        var s=postJson(submitRoute(id),TOKEN,Map.of("expectedRevision","2","schedulePreviewId",p.path("id").asText()),201);
        postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","RETURN","comment","请复核执行时段"),201);
        var next=postJson(submitRoute(id),TOKEN,Map.of("expectedRevision","4","schedulePreviewId",p.path("id").asText()),201);
        postJson("/review-submissions/"+next.path("id").asText()+"/withdrawals",REVIEWER,Map.of("expectedRevision","0","reason","不是提交人"),403);
        postJson("/review-submissions/"+next.path("id").asText()+"/withdrawals",TOKEN,Map.of("expectedRevision","0","reason","重新核实"),201);
        assertThat(getJson("/review-submissions/"+s.path("id").asText(),TOKEN,200).path("status").asText()).isEqualTo("RETURNED");
        assertThat(getJson("/task-bundles/"+id+"/review-context",TOKEN,200).path("submissions").size()).isEqualTo(2);
    }
    @Test void disablingTheTemplateDoesNotTrapTheOwnerInPendingReview() throws Exception {
        var b=readyBundle();var p=preview(b);var s=postJson(submitRoute(b.path("id").asText()),TOKEN,Map.of("expectedRevision","2","schedulePreviewId",p.path("id").asText()),201);
        db.update("UPDATE task_template SET review_status='DISABLED' WHERE id=?",b.path("groups").get(0).path("template").path("id").asText());
        postJson("/review-submissions/"+s.path("id").asText()+"/withdrawals",TOKEN,Map.of("expectedRevision","0","reason","模板失效，撤回重新编排"),201);
        assertThat(getJson("/task-bundles/"+b.path("id").asText(),TOKEN,200).path("status").asText()).isEqualTo("DRAFT");
    }
    @Test void disablingTheTemplateAllowsReturnButStillPreventsApproval() throws Exception {
        var b=readyBundle();var p=preview(b);var s=postJson(submitRoute(b.path("id").asText()),TOKEN,Map.of("expectedRevision","2","schedulePreviewId",p.path("id").asText()),201);
        db.update("UPDATE task_template SET review_status='DISABLED' WHERE id=?",b.path("groups").get(0).path("template").path("id").asText());
        postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),400);
        postJson("/review-submissions/"+s.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","RETURN","comment","模板已失效"),201);
    }
}
