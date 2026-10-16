package com.healthsystem.vo;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.List;
public record TaskBundleVO(String id,String patientId,String sourcePlanVersionId,String sourceReviewSubmissionId,
    String sourceReviewDecisionId,String sourceRevision,int versionNo,String basedOnBundleId,String rowVersion,
    String contentRevision,String status,LocalDate startDate,LocalDate endDate,String zoneId,String generationMode,
    String contentHash,String latestPreviewId,String inputSnapshotId,JsonNode inputSnapshot,JsonNode nextCycleReference,
    List<JsonNode> groups,List<PlanReviewContextVO.Issue> issues) {}
