package com.healthsystem.vo;
import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
public record ReviewSubmissionVO(String id,String planVersionId,String stage,String rowVersion,String targetRevision,String contentHash,String status,JsonNode snapshot,String submittedBy,OffsetDateTime submittedAt,String closedBy,OffsetDateTime closedAt,String closedReason,Decision decision,String taskBundleId,String schedulePreviewId) {
    public record Decision(String id,String decision,String comment,String reviewerId,OffsetDateTime reviewedAt) {}
}
