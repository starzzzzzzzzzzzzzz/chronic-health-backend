package com.healthsystem.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

public record LegacyManagedArchiveVO(
        @Schema(description = "本机持久档案编号，以字符串传输避免精度丢失", example = "1000000000000000001")
        String archiveId,
        String name,
        @Schema(description = "无结构化疾病标签来源时为空字符串") String diseaseTags,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "按上海当日计算的周岁；出生日期未知或在未来时省略", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer age) {}
