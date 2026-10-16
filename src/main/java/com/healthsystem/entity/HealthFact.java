package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("health_fact")
public class HealthFact extends ScopedEntity {
    @TableField(value = "patient_id", updateStrategy = FieldStrategy.ALWAYS)
    private String patientId;
    @TableField(value = "axis_code", updateStrategy = FieldStrategy.ALWAYS)
    private String axisCode;
    @TableField(value = "fact_code", updateStrategy = FieldStrategy.ALWAYS)
    private String factCode;
    @TableField(value = "value_json", updateStrategy = FieldStrategy.ALWAYS)
    private String valueJson;
    @TableField(value = "unit", updateStrategy = FieldStrategy.ALWAYS)
    private String unit;
    @TableField(value = "source_type", updateStrategy = FieldStrategy.ALWAYS)
    private String sourceType;
    @TableField(value = "source_locator_json", updateStrategy = FieldStrategy.ALWAYS)
    private String sourceLocatorJson;
    @TableField(value = "observed_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime observedAt;
    @TableField(value = "confidence", updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal confidence;
    @TableField(value = "confirmation_status", updateStrategy = FieldStrategy.ALWAYS)
    private String confirmationStatus;
    @TableField(value = "confirmed_by", updateStrategy = FieldStrategy.ALWAYS)
    private String confirmedBy;
    @TableField(value = "confirmed_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime confirmedAt;
    @TableField(value = "supersedes_fact_id", updateStrategy = FieldStrategy.ALWAYS)
    private String supersedesFactId;
    @TableField(value = "conflict_group_key", updateStrategy = FieldStrategy.ALWAYS)
    private String conflictGroupKey;
    public String getPatientId() { return patientId; }
    public void setPatientId(String value) { this.patientId = value; }
    public String getAxisCode() { return axisCode; }
    public void setAxisCode(String value) { this.axisCode = value; }
    public String getFactCode() { return factCode; }
    public void setFactCode(String value) { this.factCode = value; }
    public String getValueJson() { return valueJson; }
    public void setValueJson(String value) { this.valueJson = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { this.unit = value; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { this.sourceType = value; }
    public String getSourceLocatorJson() { return sourceLocatorJson; }
    public void setSourceLocatorJson(String value) { this.sourceLocatorJson = value; }
    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime value) { this.observedAt = value; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal value) { this.confidence = value; }
    public String getConfirmationStatus() { return confirmationStatus; }
    public void setConfirmationStatus(String value) { this.confirmationStatus = value; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String value) { this.confirmedBy = value; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime value) { this.confirmedAt = value; }
    public String getSupersedesFactId() { return supersedesFactId; }
    public void setSupersedesFactId(String value) { this.supersedesFactId = value; }
    public String getConflictGroupKey() { return conflictGroupKey; }
    public void setConflictGroupKey(String value) { this.conflictGroupKey = value; }
}
