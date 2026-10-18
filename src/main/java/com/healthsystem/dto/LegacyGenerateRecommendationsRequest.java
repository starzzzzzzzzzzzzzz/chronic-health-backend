package com.healthsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;
import java.util.List;

public record LegacyGenerateRecommendationsRequest(
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}")
        @Schema(type = "integer", format = "int64", description = "旧文档为 int64；也接受精确十进制字符串，避免前端大整数精度丢失") String archiveId,
        @Size(max = 500) String disease,
        @Size(max = 10000) String prompt,
        @Size(max = 20) List<@NotBlank @Size(max = 200) String> focuses,
        @Pattern(regexp = "[1-9][0-9]{0,18}") @Schema(type = "integer", format = "int64") String shopId,
        @Pattern(regexp = "[1-9][0-9]{0,18}") @Schema(type = "integer", format = "int64") String teamId,
        @Size(max = 10000) String supplementalRequirements,
        @ArraySchema(arraySchema=@Schema(description="病种侧重确认；原样返回 evaluation/sourceHash，adjustmentReason 必填"),schema=@Schema(implementation=ConfirmedManagementRequest.class),maxItems=20) JsonNode confirmedManagement) {}
