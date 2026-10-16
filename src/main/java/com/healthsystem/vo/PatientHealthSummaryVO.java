package com.healthsystem.vo;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
public record PatientHealthSummaryVO(PatientSummaryVO patient,String profileRevision,Map<String,AxisVO> axes,List<FactVO> facts,List<ConflictVO> conflicts,List<String> missingFields) {
    public record AxisVO(String status,List<String> factIds) {}
    public record ConflictVO(String key,List<String> factIds) {}
    public record FactVO(String id,String axisCode,String factCode,JsonNode value,String unit,String sourceType,JsonNode sourceRef,OffsetDateTime observedAt,BigDecimal confidence,String confirmationStatus,String confirmedBy,OffsetDateTime confirmedAt) {}
}
