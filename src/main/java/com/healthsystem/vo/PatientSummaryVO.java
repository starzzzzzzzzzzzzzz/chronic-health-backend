package com.healthsystem.vo;
import java.time.LocalDate;
public record PatientSummaryVO(String id,String name,String sex,LocalDate birthDate,String dataMode,String profileRevision) {}
