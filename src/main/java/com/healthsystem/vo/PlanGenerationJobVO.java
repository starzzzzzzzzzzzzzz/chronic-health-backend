package com.healthsystem.vo;
import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
public record PlanGenerationJobVO(String id,String planVersionId,String status,String baseRevision,
    String inputSnapshotId,String model,String promptVersion,String ruleVersion,boolean requiresHumanReview,
    JsonNode candidate,JsonNode issues,JsonNode usage,String errorCode,String retryOfJobId,
    OffsetDateTime createdAt,OffsetDateTime startedAt,OffsetDateTime finishedAt,OffsetDateTime appliedAt) {
    public PlanGenerationJobVO {
        // 兼容已有生成作业的英文校验提示，只改返回副本，不改已保存的候选或来源快照。
        if (issues != null && issues.isObject() && issues.path("validationErrors").isArray()) {
            var localized = (tools.jackson.databind.node.ObjectNode) issues.deepCopy();
            var errors = localized.putArray("validationErrors");
            for (var error : issues.path("validationErrors")) {
                if (error.isString()) errors.add(com.healthsystem.common.api.UserMessages.chinese(error.asText()));
                else errors.add(error);
            }
            issues = localized;
        }
    }
}
