package com.healthsystem.vo;
import java.util.List;

public record ManagementCoordinationReportVO(String status,String inputHash,List<Coverage> coverage,List<CoordinationIssue> issues,String note) {
    public record Coverage(String disease,String status,String reason,List<Object> references,List<String> repairDimensions) {}
    public record CoordinationIssue(String kind,String reason,List<Object> references) {}
}
