package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("care_plan_module")
public class CarePlanModule {
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
    @TableField(value = "module_code", updateStrategy = FieldStrategy.ALWAYS)
    private String moduleCode;
    @TableField(value = "selection_reason", updateStrategy = FieldStrategy.ALWAYS)
    private String selectionReason;
    @TableField(value = "basis_json", updateStrategy = FieldStrategy.ALWAYS)
    private String basisJson;
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
    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String value) { this.moduleCode = value; }
    public String getSelectionReason() { return selectionReason; }
    public void setSelectionReason(String value) { this.selectionReason = value; }
    public String getBasisJson() { return basisJson; }
    public void setBasisJson(String value) { this.basisJson = value; }
}
