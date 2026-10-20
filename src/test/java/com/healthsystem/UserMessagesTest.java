package com.healthsystem;

import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.api.UserMessages;
import com.healthsystem.common.json.JsonValues;
import com.healthsystem.entity.SchedulePreview;
import com.healthsystem.service.impl.TaskScheduleService;
import com.healthsystem.vo.PlanGenerationJobVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMessagesTest {
    @Test void translatesEnvelopeButDoesNotTouchMachineValuesOrPayload() {
        var data = JsonValues.read("{\"status\":\"APPROVED\",\"errorCode\":\"AI_TIMEOUT\",\"note\":\"Invalid groups\"}");
        var response = new ApiResponse<>(409, "Publication preview is stale; inspect a new replacement manifest", data);
        assertThat(response.code()).isEqualTo(409);
        assertThat(response.message()).isEqualTo("发布预览已过期，请重新预览并核对任务替换清单");
        assertThat(response.data()).isSameAs(data);
        assertThat(response.data().path("note").asText()).isEqualTo("Invalid groups");
        assertThat(UserMessages.chinese("AI_TIMEOUT")).isEqualTo("AI_TIMEOUT");
        assertThat(UserMessages.chinese("已确认")).isEqualTo("已确认");
    }

    @Test void preservesDynamicFieldNamesAndProviderErrorCodes() {
        assertThat(UserMessages.chinese("Invalid expectedRevision")).isEqualTo("字段格式不正确：expectedRevision");
        assertThat(UserMessages.chinese("Unknown request field: groupId")).isEqualTo("请求包含不支持的字段：groupId");
        assertThat(UserMessages.chinese("AI candidate unavailable: AI_TIMEOUT")).isEqualTo("AI 候选方案暂不可用，错误码：AI_TIMEOUT");
        assertThat(UserMessages.chinese("Invalid groups")).isEqualTo("任务组列表不符合要求");
        assertThat(UserMessages.chinese("Confirmation note required")).isEqualTo("请填写执行确认说明");
        assertThat(UserMessages.chinese("Self-review is not allowed")).isEqualTo("不能审核自己提交的内容，请由其他审核人员处理");
        assertThat(UserMessages.chinese("Existing tasks require explicit ALL_FUTURE replacement scope")).contains("ALL_FUTURE","已有执行任务");
    }

    @Test void readsOldEnglishPreviewWithoutChangingStoredJsonOrHash() {
        var row = new SchedulePreview();
        row.setId("preview");row.setTaskBundleId("bundle");row.setBundleRevision(1L);
        row.setContentHash("content");row.setRuleVersion("task-rules-demo-v1");
        row.setOccurrencesJson("[]");
        String original = "[{\"code\":\"SCHEDULE_REQUIRED\",\"severity\":\"BLOCKER\",\"fieldPath\":\"groups.0\",\"message\":\"Confirm execution days, slots and windows\"}]";
        row.setIssuesJson(original);
        row.setPreviewHash(TaskScheduleService.hash(row));
        String hash = row.getPreviewHash();
        var view = TaskScheduleService.view(row);
        assertThat(view.issues().get(0).message()).isEqualTo("请确认执行日期、时段和允许执行的时间范围");
        assertThat(view.issues().get(0).code()).isEqualTo("SCHEDULE_REQUIRED");
        assertThat(view.issues().get(0).severity()).isEqualTo("BLOCKER");
        assertThat(view.issues().get(0).fieldPath()).isEqualTo("groups.0");
        assertThat(row.getIssuesJson()).isEqualTo(original);
        assertThat(TaskScheduleService.hash(row)).isEqualTo(hash);
        assertThat(view.previewHash()).isEqualTo(hash);
    }

    @Test void readsOldGenerationValidationErrorsWithoutMutatingCandidateOrStoredIssues() {
        var issues = JsonValues.read("{\"validationErrors\":[\"AI cannot invent reference sources\"],\"warnings\":[\"original warning\"]}");
        var candidate = JsonValues.read("{\"primaryGoal\":\"original content\"}");
        var view = new PlanGenerationJobVO("job","version","FAILED","1","snapshot","model","prompt","rule",true,
                candidate,issues,null,"AI_OUTPUT_REJECTED",null,null,null,null,null);
        assertThat(view.issues().path("validationErrors").get(0).asText()).isEqualTo("AI 不能编造参考来源");
        assertThat(issues.path("validationErrors").get(0).asText()).isEqualTo("AI cannot invent reference sources");
        assertThat(view.issues().path("warnings")).isEqualTo(issues.path("warnings"));
        assertThat(view.candidate()).isSameAs(candidate);
        assertThat(view.errorCode()).isEqualTo("AI_OUTPUT_REJECTED");
        assertThat(view.status()).isEqualTo("FAILED");
    }
}
