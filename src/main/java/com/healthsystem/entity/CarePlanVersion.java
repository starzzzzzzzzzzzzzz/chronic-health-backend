package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("care_plan_version")
public class CarePlanVersion extends ScopedEntity {
    @TableField(value = "updated_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updatedAt;
    @TableField(value = "row_version", updateStrategy = FieldStrategy.ALWAYS)
    private Long rowVersion;
    @TableField(value = "plan_id", updateStrategy = FieldStrategy.ALWAYS)
    private String planId;
    @TableField(value = "version_no", updateStrategy = FieldStrategy.ALWAYS)
    private Integer versionNo;
    @TableField(value = "based_on_version_id", updateStrategy = FieldStrategy.ALWAYS)
    private String basedOnVersionId;
    @TableField(value = "title", updateStrategy = FieldStrategy.ALWAYS)
    private String title;
    @TableField(value = "start_date", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate startDate;
    @TableField(value = "end_date", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate endDate;
    @TableField(value = "zone_id", updateStrategy = FieldStrategy.ALWAYS)
    private String zoneId;
    @TableField(value = "primary_goal", updateStrategy = FieldStrategy.ALWAYS)
    private String primaryGoal;
    @TableField(value = "goal_basis_json", updateStrategy = FieldStrategy.ALWAYS)
    private String goalBasisJson;
    @TableField(value = "supplemental_requirements", updateStrategy = FieldStrategy.ALWAYS)
    private String supplementalRequirements;
    @TableField(value = "input_snapshot_id", updateStrategy = FieldStrategy.ALWAYS)
    private String inputSnapshotId;
    @TableField(value = "status", updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(value = "content_hash", updateStrategy = FieldStrategy.ALWAYS)
    private String contentHash;
    @TableField(value = "change_reason", updateStrategy = FieldStrategy.ALWAYS)
    private String changeReason;
    @TableField(value = "content_revision", updateStrategy = FieldStrategy.ALWAYS)
    private Long contentRevision;
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { this.rowVersion = value; }
    public String getPlanId() { return planId; }
    public void setPlanId(String value) { this.planId = value; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer value) { this.versionNo = value; }
    public String getBasedOnVersionId() { return basedOnVersionId; }
    public void setBasedOnVersionId(String value) { this.basedOnVersionId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate value) { this.startDate = value; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate value) { this.endDate = value; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String value) { this.zoneId = value; }
    public String getPrimaryGoal() { return primaryGoal; }
    public void setPrimaryGoal(String value) { this.primaryGoal = value; }
    public String getGoalBasisJson() { return goalBasisJson; }
    public void setGoalBasisJson(String value) { this.goalBasisJson = value; }
    public String getSupplementalRequirements() { return supplementalRequirements; }
    public void setSupplementalRequirements(String value) { this.supplementalRequirements = value; }
    public String getInputSnapshotId() { return inputSnapshotId; }
    public void setInputSnapshotId(String value) { this.inputSnapshotId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String value) { this.contentHash = value; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String value) { this.changeReason = value; }
    public Long getContentRevision() { return contentRevision; }
    public void setContentRevision(Long value) { this.contentRevision = value; }
}
