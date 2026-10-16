package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("health_input_snapshot")
public class HealthInputSnapshot {
    @TableId(type = IdType.INPUT)
    private String id;
    @TableField(value = "org_id", updateStrategy = FieldStrategy.ALWAYS)
    private String orgId;
    @TableField(value = "created_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime createdAt;
    @TableField(value = "created_by", updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;
    @TableField(value = "patient_id", updateStrategy = FieldStrategy.ALWAYS)
    private String patientId;
    @TableField(value = "stage", updateStrategy = FieldStrategy.ALWAYS)
    private String stage;
    @TableField(value = "profile_revision", updateStrategy = FieldStrategy.ALWAYS)
    private Long profileRevision;
    @TableField(value = "captured_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime capturedAt;
    @TableField(value = "schema_version", updateStrategy = FieldStrategy.ALWAYS)
    private String schemaVersion;
    @TableField(value = "payload_json", updateStrategy = FieldStrategy.ALWAYS)
    private String payloadJson;
    @TableField(value = "payload_hash", updateStrategy = FieldStrategy.ALWAYS)
    private String payloadHash;
    public String getId() { return id; }
    public void setId(String value) { this.id = value; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String value) { this.orgId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { this.createdBy = value; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String value) { this.patientId = value; }
    public String getStage() { return stage; }
    public void setStage(String value) { this.stage = value; }
    public Long getProfileRevision() { return profileRevision; }
    public void setProfileRevision(Long value) { this.profileRevision = value; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime value) { this.capturedAt = value; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String value) { this.schemaVersion = value; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String value) { this.payloadJson = value; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String value) { this.payloadHash = value; }
}
