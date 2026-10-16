package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("patient_access_grant")
public class PatientAccessGrant {
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
    @TableField(value = "subject_id", updateStrategy = FieldStrategy.ALWAYS)
    private String subjectId;
    @TableField(value = "capability", updateStrategy = FieldStrategy.ALWAYS)
    private String capability;
    @TableField(value = "valid_from", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime validFrom;
    @TableField(value = "valid_until", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime validUntil;
    @TableField(value = "revoked_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime revokedAt;
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
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String value) { this.subjectId = value; }
    public String getCapability() { return capability; }
    public void setCapability(String value) { this.capability = value; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime value) { this.validFrom = value; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime value) { this.validUntil = value; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime value) { this.revokedAt = value; }
}
