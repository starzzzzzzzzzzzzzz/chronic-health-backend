package com.healthsystem.vo;

import java.util.List;
import java.util.Map;

/** Fields match the frontend Evaluated/Disease/Option/Match contract. Weights here are integer percentages. */
public record ManagementBasisVO(String archiveId, String sourceHash, List<Disease> diseases,
        String medicationFacts, LegacyRecommendationsVO.DecisionExplanation decisionExplanation) {
    public record Disease(String name, String risk, String subtype, String priority, String basis,
            Map<String,Integer> weights, List<Option> managementTypes, List<Match> typeMatches,
            List<String> secondarySubtypes, String ruleVersion) {}
    public record Option(String name, String condition, Map<String,Integer> initialWeights) {}
    public record Match(String name, String status, String reason, String missing, List<Evidence> evidence) {}
    public record Evidence(String source, String quote) {}
}
