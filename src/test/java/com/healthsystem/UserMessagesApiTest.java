package com.healthsystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class UserMessagesApiTest extends TaskApiTestSupport {
    @Test void planReviewContextExposesChineseIssueWithStableCodeAndFieldPath() throws Exception {
        var f=draft("routine",true);
        var context=getJson("/plan-versions/"+f.version()+"/review-context",TOKEN,200);
        var issue=context.path("issues").get(0);
        assertThat(issue.path("code").asText()).isEqualTo("CONDITION_NOT_CONFIRMED");
        assertThat(issue.path("severity").asText()).isEqualTo("BLOCKER");
        assertThat(issue.path("fieldPath").asText()).startsWith("conditions.");
        assertThat(issue.path("message").asText()).isEqualTo("必要执行条件缺失、已过期或尚未确认");
    }

    @Test void scheduleCreateReadAndTaskReviewContextReturnSameChineseIssues() throws Exception {
        var f=approved("routine",true);var b=bundle(f,"4");var p=preview(b);
        assertThat(p.path("issues").toString()).contains("SCHEDULE_REQUIRED","请确认执行日期、时段和允许执行的时间范围");
        var read=getJson("/schedule-previews/"+p.path("id").asText(),TOKEN,200);
        var context=getJson("/task-bundles/"+b.path("id").asText()+"/review-context",TOKEN,200);
        assertThat(read.path("issues")).isEqualTo(p.path("issues"));
        assertThat(read.path("previewHash")).isEqualTo(p.path("previewHash"));
        assertThat(context.path("schedulePreview").path("issues")).isEqualTo(p.path("issues"));
    }

    @Test void malformedGroupsAndMissingConfirmationNoteKeep400WithChineseMessage() throws Exception {
        var f=approved("routine",true);var b=bundle(f,"4");
        mvc.perform(patch("/task-bundles/"+b.path("id").asText()).header("Authorization",TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision","0","groups","bad"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("任务组列表不符合要求"));
        mvc.perform(patch("/task-bundles/"+b.path("id").asText()).header("Authorization",TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision","0","groups",
                        List.of(Map.of("id",b.path("groups").get(0).path("id").asText(),"executionConfirmed",true))))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("请填写执行确认说明"));
    }

    @Test void selfReviewStillReturns403WithChineseMessageEvenWithReviewGrant() throws Exception {
        var f=draft("routine",false);var s=submit(f,"1");grant(f.patient(),"demo-clinician","PLAN_REVIEW");
        mvc.perform(post("/review-submissions/"+s.path("id").asText()+"/decisions").header("Authorization",TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":\"0\",\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("不能审核自己提交的内容，请由其他审核人员处理"));
    }
}
