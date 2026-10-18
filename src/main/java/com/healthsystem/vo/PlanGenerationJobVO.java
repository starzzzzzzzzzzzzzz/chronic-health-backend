package com.healthsystem.vo;
import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
public record PlanGenerationJobVO(String id,String planVersionId,String status,String baseRevision,
    String inputSnapshotId,String model,String promptVersion,String ruleVersion,boolean requiresHumanReview,
    JsonNode candidate,JsonNode issues,JsonNode usage,String errorCode,String retryOfJobId,
    OffsetDateTime createdAt,OffsetDateTime startedAt,OffsetDateTime finishedAt,OffsetDateTime appliedAt) {}
