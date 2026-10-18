package com.healthsystem.vo;

import java.util.List;

public record LegacyRecommendationsVO(String adviceId, String retrievalMode,
        List<RecommendationItem> recommendations, List<String> warnings, String generationStatus,
        List<QualityIssue> qualityIssues, DecisionExplanation decisionExplanation, ManagementCoordinationReportVO coordinationReport) {
    public record RecommendationItem(String id, String category, String text, int evidenceCount,
            List<Object> actions, boolean decompositionPending, String originalText) {}
    public record QualityIssue(String kind, String reason, List<Object> references) {}
    public record DecisionExplanation(String analysisBasis, String decisionSummary, String pendingConfirmation) {}
}
