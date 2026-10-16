package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("care_plan")
public class CarePlan extends ScopedEntity {
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
