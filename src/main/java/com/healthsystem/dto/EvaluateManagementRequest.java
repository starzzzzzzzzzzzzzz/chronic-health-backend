package com.healthsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record EvaluateManagementRequest(
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") @Schema(type="integer",format="int64") String archiveId,
        @Pattern(regexp="[1-9][0-9]{0,18}") @Schema(type="integer",format="int64") String shopId,
        @Pattern(regexp="[1-9][0-9]{0,18}") @Schema(type="integer",format="int64") String teamId) {}
