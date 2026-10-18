package com.healthsystem.vo;
import tools.jackson.databind.JsonNode;
import java.util.List;
public record TaskSchedulePreviewVO(String id,String taskBundleId,String bundleRevision,String contentHash,String previewHash,
    String ruleVersion,JsonNode occurrences,List<PlanReviewContextVO.Issue> issues) {}
