package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("care_plan_dimension")
public class CarePlanDimension {
    @TableId(type = IdType.INPUT)
    private String id;
    @TableField(value = "org_id", updateStrategy = FieldStrategy.ALWAYS)
    private String orgId;
    @TableField(value = "created_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime createdAt;
    @TableField(value = "created_by", updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;
    @TableField(value = "plan_version_id", updateStrategy = FieldStrategy.ALWAYS)
    private String planVersionId;
    @TableField(value = "dimension_code", updateStrategy = FieldStrategy.ALWAYS)
    private String dimensionCode;
    @TableField(value = "decision", updateStrategy = FieldStrategy.ALWAYS)
    private String decision;
    @TableField(value = "decision_reason", updateStrategy = FieldStrategy.ALWAYS)
    private String decisionReason;
    @TableField(value = "sort_order", updateStrategy = FieldStrategy.ALWAYS)
    private Integer sortOrder;
    public String getId() { return id; }
    public void setId(String value) { this.id = value; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String value) { this.orgId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { this.createdBy = value; }
    public String getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(String value) { this.planVersionId = value; }
    public String getDimensionCode() { return dimensionCode; }
    public void setDimensionCode(String value) { this.dimensionCode = value; }
    public String getDecision() { return decision; }
    public void setDecision(String value) { this.decision = value; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String value) { this.decisionReason = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { this.sortOrder = value; }
}
