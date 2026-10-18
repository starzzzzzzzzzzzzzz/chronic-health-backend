package com.healthsystem.dto;

import com.healthsystem.vo.ManagementBasisVO.Disease;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** Documentation shape; raw input is validated without lossy coercion by ManagementFocusPolicy. */
public record ConfirmedManagementRequest(Disease evaluation,
        @Schema(description="原样返回依据评估的 sourceHash") String sourceHash,
        String primarySubtype,List<String> secondarySubtypes,
        @Schema(description="人工核对/纠正依据，必填") String adjustmentReason,
        @Schema(allowableValues={"重点管理","加强管理","常规管理"}) String priority,
        @Schema(description="七维整数百分比，总和100；省略时取主亚型文档初始权重") Map<String,Integer> weights) {}
