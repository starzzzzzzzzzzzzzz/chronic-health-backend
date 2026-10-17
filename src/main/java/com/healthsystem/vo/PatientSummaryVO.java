package com.healthsystem.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
public record PatientSummaryVO(
        String id,
        String name,
        String sex,
        @Schema(description="出生日期，未知时为 null",nullable=true) LocalDate birthDate,
        String dataMode,
        String profileRevision) {}
