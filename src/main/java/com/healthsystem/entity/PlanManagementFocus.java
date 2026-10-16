package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("plan_management_focus")
public class PlanManagementFocus extends ScopedEntity {
    @TableField(value = "plan_version_id", updateStrategy = FieldStrategy.ALWAYS)
    private String planVersionId;
    @TableField(value = "diagnosis_fact_id", updateStrategy = FieldStrategy.ALWAYS)
    private String diagnosisFactId;
    @TableField(value = "primary_subtype_code", updateStrategy = FieldStrategy.ALWAYS)
    private String primarySubtypeCode;
    @TableField(value = "secondary_subtypes_json", updateStrategy = FieldStrategy.ALWAYS)
    private String secondarySubtypesJson;
    @TableField(value = "priority_code", updateStrategy = FieldStrategy.ALWAYS)
    private String priorityCode;
    @TableField(value = "dimension_weights_json", updateStrategy = FieldStrategy.ALWAYS)
    private String dimensionWeightsJson;
    @TableField(value = "contribution_bps", updateStrategy = FieldStrategy.ALWAYS)
    private Integer contributionBps;
    @TableField(value = "adjustment_reason", updateStrategy = FieldStrategy.ALWAYS)
    private String adjustmentReason;
    public String getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(String value) { this.planVersionId = value; }
    public String getDiagnosisFactId() { return diagnosisFactId; }
    public void setDiagnosisFactId(String value) { this.diagnosisFactId = value; }
    public String getPrimarySubtypeCode() { return primarySubtypeCode; }
    public void setPrimarySubtypeCode(String value) { this.primarySubtypeCode = value; }
    public String getSecondarySubtypesJson() { return secondarySubtypesJson; }
    public void setSecondarySubtypesJson(String value) { this.secondarySubtypesJson = value; }
    public String getPriorityCode() { return priorityCode; }
    public void setPriorityCode(String value) { this.priorityCode = value; }
    public String getDimensionWeightsJson() { return dimensionWeightsJson; }
    public void setDimensionWeightsJson(String value) { this.dimensionWeightsJson = value; }
    public Integer getContributionBps() { return contributionBps; }
    public void setContributionBps(Integer value) { this.contributionBps = value; }
    public String getAdjustmentReason() { return adjustmentReason; }
    public void setAdjustmentReason(String value) { this.adjustmentReason = value; }
}
