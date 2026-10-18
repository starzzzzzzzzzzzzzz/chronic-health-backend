package com.healthsystem.controller;

import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.LegacyGenerateRecommendationsRequest;
import com.healthsystem.service.impl.LegacyRecommendationService;
import com.healthsystem.vo.LegacyRecommendationsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "12 旧接口兼容：资料与建议")
public class LegacyRecommendationController {
    private final LegacyRecommendationService service;
    public LegacyRecommendationController(LegacyRecommendationService service) { this.service = service; }
    @PostMapping("/store/care/drafts/recommendations/generate")
    @Operation(summary = "按旧契约生成 AI 方案建议", description = "同步等待真实候选，成功为 200。使用 X-Request-Id 保证相同请求重放不重复计费；等待超时返回 504，保留原键重试。adviceId 对应生成作业；动作仍待后续拆解，confirmedManagement 须由依据评估的 sourceHash 绑定来源。category 暂使用七维中文名，generationStatus=SUCCEEDED；页面枚举取值待伙伴确认。")
    public ApiResponse<LegacyRecommendationsVO> generate(
            @Parameter(hidden = true) @AuthenticationPrincipal BusinessPrincipal principal,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody LegacyGenerateRecommendationsRequest request) {
        return ApiResponse.success(service.generate(principal, requestId == null ? MDC.get("requestId") : requestId, request));
    }
}
