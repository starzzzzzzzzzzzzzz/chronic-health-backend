package com.healthsystem.service.impl;

import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.LegacyGenerateRecommendationsRequest;
import com.healthsystem.exception.BusinessException;
import com.healthsystem.vo.LegacyRecommendationsVO;
import com.healthsystem.vo.LegacyRecommendationsVO.*;
import com.healthsystem.vo.PlanGenerationJobVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class LegacyRecommendationService {
    private static final Map<String, String> CATEGORY = Map.of("medication", "用药", "diet", "饮食", "exercise", "运动",
            "routine", "起居", "monitoring", "监测", "tcm", "中医", "other", "其他");
    private final LegacyRecommendationStore store;
    private final PlanGenerationService generations;
    private final long waitMs;
    public LegacyRecommendationService(LegacyRecommendationStore store, PlanGenerationService generations,
            @Value("${health.legacy.recommendation-wait-ms:120000}") long waitMs) {
        this.store = store; this.generations = generations; this.waitMs = Math.max(0, Math.min(180000, waitMs));
    }
    // Intentionally outside a database transaction: the existing worker owns the external call.
    public LegacyRecommendationsVO generate(BusinessPrincipal principal, String key, LegacyGenerateRecommendationsRequest request) {
        var job = store.submit(principal, key, request);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMs);
        while (Set.of("PENDING", "RUNNING").contains(job.status())) {
            if (System.nanoTime() >= deadline) throw new BusinessException(504,
                    "AI generation is still pending; retry the identical body with the same X-Request-Id");
            try { Thread.sleep(250); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(503, "Generation wait interrupted; retry with the same X-Request-Id");
            }
            job = generations.get(principal, job.id());
        }
        if (!Set.of("SUCCEEDED", "APPLIED").contains(job.status()))
            throw new BusinessException("AI_TIMEOUT".equals(job.errorCode()) ? 504 : 502,
                    "AI candidate unavailable: " + Objects.toString(job.errorCode(), "AI_OUTPUT_REJECTED"));
        return project(job);
    }
    static LegacyRecommendationsVO project(PlanGenerationJobVO job) {
        var candidate = job.candidate();
        List<RecommendationItem> items = new ArrayList<>();
        int index = 0;
        for (var item : candidate.path("items")) {
            index++;
            if (!item.path("isSelected").asBoolean()) continue;
            String text = item.path("title").asText() + "\n" + item.path("content").asText();
            // Citation count is not a count of patient facts; no synthetic frequency or actions.
            items.add(new RecommendationItem(job.id() + ":" + index, CATEGORY.get(item.path("dimensionCode").asText()),
                    text, item.path("references").size(), List.of(), true, text));
        }
        List<String> warnings = new ArrayList<>(strings(job.issues().path("warnings")));
        warnings.add("当前仅为待人工审核的 AI 建议；动作拆解与工作流审核发布尚未通过旧入口适配。");
        var missing = strings(job.issues().path("missingInformation"));
        var coordination=ManagementCoordinationPolicy.report(candidate.path("items"),candidate.path("goalBasis").path("managementContext"));
        List<QualityIssue> issues = new ArrayList<>();
        for(var issue:coordination.issues())issues.add(new QualityIssue(issue.kind(),issue.reason(),issue.references()));
        for (String question : missing) issues.add(new QualityIssue("MISSING_INFORMATION", question, List.of()));
        for (var dimension : candidate.path("dimensions")) {
            if (!"ENABLED".equals(dimension.path("decision").asText()))
                issues.add(new QualityIssue(dimension.path("decision").asText(),
                        CATEGORY.get(dimension.path("dimensionCode").asText()) + "：" + dimension.path("decisionReason").asText(), List.of()));
        }
        return new LegacyRecommendationsVO(job.id(), "MODEL_ONLY", items, warnings, "SUCCEEDED", issues,
                new DecisionExplanation("依据已授权患者的冻结输入资料生成；未接入文献检索知识库。",
                        candidate.path("primaryGoal").asText(), String.join("\n", missing)),coordination);
    }
    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>(); if (node != null && node.isArray()) for (var value : node) values.add(value.asText());
        return values;
    }
}
