package com.healthsystem.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record CreateCarePlanRequest(
 @NotBlank String patientId,
 @NotBlank @Pattern(regexp="0|[1-9][0-9]{0,18}") String expectedProfileRevision,
 @NotBlank @Size(max=200) String title,
 @NotNull LocalDate startDate,@NotNull LocalDate endDate,
 @NotBlank String zoneId,@Size(max=1000) String primaryGoal) {}
