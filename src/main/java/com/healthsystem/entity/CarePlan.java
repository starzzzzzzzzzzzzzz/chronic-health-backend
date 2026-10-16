package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("care_plan")
public class CarePlan {
    @TableId(type = IdType.INPUT)
    private String id;
    @TableField(value = "org_id", updateStrategy = FieldStrategy.ALWAYS)
    private String orgId;
    @TableField(value = "created_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime createdAt;
    @TableField(value = "created_by", updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;
    @TableField(value = "updated_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updatedAt;
    @TableField(value = "row_version", updateStrategy = FieldStrategy.ALWAYS)
    private Long rowVersion;
    @TableField(value = "patient_id", updateStrategy = FieldStrategy.ALWAYS)
    private String patientId;
    @TableField(value = "plan_no", updateStrategy = FieldStrategy.ALWAYS)
    private String planNo;
    @TableField(value = "current_version_id", updateStrategy = FieldStrategy.ALWAYS)
    private String currentVersionId;
    @TableField(value = "status", updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(value = "closed_reason", updateStrategy = FieldStrategy.ALWAYS)
    private String closedReason;
    public String getId() { return id; }
    public void setId(String value) { this.id = value; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String value) { this.orgId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { this.createdBy = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { this.rowVersion = value; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String value) { this.patientId = value; }
    public String getPlanNo() { return planNo; }
    public void setPlanNo(String value) { this.planNo = value; }
    public String getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(String value) { this.currentVersionId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getClosedReason() { return closedReason; }
    public void setClosedReason(String value) { this.closedReason = value; }
}
