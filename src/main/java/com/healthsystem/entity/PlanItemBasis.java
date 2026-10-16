package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("plan_item_basis")
public class PlanItemBasis {
    @TableId(type = IdType.INPUT)
    private String id;
    @TableField(value = "org_id", updateStrategy = FieldStrategy.ALWAYS)
    private String orgId;
    @TableField(value = "created_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime createdAt;
    @TableField(value = "created_by", updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;
    @TableField(value = "plan_item_id", updateStrategy = FieldStrategy.ALWAYS)
    private String planItemId;
    @TableField(value = "module_id", updateStrategy = FieldStrategy.ALWAYS)
    private String moduleId;
    @TableField(value = "fact_id", updateStrategy = FieldStrategy.ALWAYS)
    private String factId;
    @TableField(value = "basis_note", updateStrategy = FieldStrategy.ALWAYS)
    private String basisNote;
    public String getId() { return id; }
    public void setId(String value) { this.id = value; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String value) { this.orgId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { this.createdBy = value; }
    public String getPlanItemId() { return planItemId; }
    public void setPlanItemId(String value) { this.planItemId = value; }
    public String getModuleId() { return moduleId; }
    public void setModuleId(String value) { this.moduleId = value; }
    public String getFactId() { return factId; }
    public void setFactId(String value) { this.factId = value; }
    public String getBasisNote() { return basisNote; }
    public void setBasisNote(String value) { this.basisNote = value; }
}
