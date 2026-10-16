package com.healthsystem.config;

import com.healthsystem.dto.PlanComponents.*;
import com.healthsystem.dto.PlanComponents.Module;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Documentation schemas for presence-preserving JSON bodies; runtime validation stays in the services. */
public final class ApiDocumentationModels {
    private ApiDocumentationModels() {}
    @Schema(name="PlanPatchRequest", description="省略字段保持原值；空数组清空集合，集合不能为 null。子项 ID 必须属于当前版本。")
    public record PlanPatch(
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,description="当前方案的 rowVersion",example="0",pattern="0|[1-9][0-9]{0,18}") String expectedRevision,
        String title,LocalDate startDate,LocalDate endDate,String zoneId,
        @Schema(nullable=true) String primaryGoal,
        @Schema(nullable=true) GoalBasis goalBasis,
        @Schema(nullable=true) String supplementalRequirements,
        @Schema(nullable=true) String changeReason,
        List<Module> modules,List<Focus> managementFocuses,List<Dimension> dimensions,List<Item> items) {}
    @Schema(name="GoalBasis") public record GoalBasis(List<String> factIds) {}
    @Schema(name="ConditionConfirmationRequest")
    public record ConfirmationBatch(
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,description="当前方案的 rowVersion",example="1") String expectedRevision,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,description="1～1000 条，整批原子提交") List<ConfirmationEntry> entries) {}
    @Schema(name="ConditionConfirmationEntry")
    public record ConfirmationEntry(
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,description="从查询执行条件接口复制 conditionKey") String conditionKey,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,allowableValues={"PENDING","CONFIRMED","REJECTED"}) String status,
        @Schema(description="确认 EXECUTION 时填写：actorType 与条目一致，available=true，environmentConfirmed=true。待确认、拒绝或用药来源导入时省略。") Map<String,Object> value,
        @Schema(description="仅 MEDICATION_SOURCE：条目依据中可信快照的 medication_order 事实 ID，服务端导入来源内容。") String sourceFactId,
        @Schema(description="CONFIRMED/REJECTED 必填，最长 1000 字") String confirmationNote) {}
    @Schema(name="ReviewSubmissionRequest")
    public record Submission(@Schema(requiredMode=Schema.RequiredMode.REQUIRED,description="当前方案的 rowVersion",example="2") String expectedRevision) {}
    @Schema(name="ReviewDecisionRequest")
    public record Decision(
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,description="本次审核提交的 rowVersion，区别于方案修订号",example="0") String expectedRevision,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,allowableValues={"APPROVE","RETURN"}) String decision,
        @Schema(description="RETURN 时必填，最长 20000 字") String comment) {}
    @Schema(name="ReviewWithdrawalRequest")
    public record Withdrawal(
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,description="本次审核提交的 rowVersion",example="0") String expectedRevision,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,maxLength=1000) String reason) {}
    @Schema(name="NewPlanVersionRequest")
    public record NewVersion(
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,description="当前 APPROVED 来源方案版本 ID") String sourceVersionId,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,description="来源方案版本的 rowVersion") String expectedRevision,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,maxLength=1000) String changeReason) {}
}
