package com.healthsystem.dto;
import jakarta.validation.constraints.NotBlank;
public record ApplyGeneratedPlanRequest(@NotBlank String expectedRevision) {}
