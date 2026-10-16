package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("review_submission")
public class ReviewSubmission extends ScopedEntity {
    @TableField(value="updated_at", updateStrategy=FieldStrategy.ALWAYS)
    private LocalDateTime updatedAt;
    public LocalDateTime getUpdatedAt() {return updatedAt;}
    public void setUpdatedAt(LocalDateTime value) {updatedAt=value;}
    @TableField(value="row_version", updateStrategy=FieldStrategy.ALWAYS)
    private Long rowVersion;
    public Long getRowVersion() {return rowVersion;}
    public void setRowVersion(Long value) {rowVersion=value;}
    @TableField(value="stage", updateStrategy=FieldStrategy.ALWAYS)
    private String stage;
    public String getStage() {return stage;}
    public void setStage(String value) {stage=value;}
    @TableField(value="plan_version_id", updateStrategy=FieldStrategy.ALWAYS)
    private String planVersionId;
    public String getPlanVersionId() {return planVersionId;}
    public void setPlanVersionId(String value) {planVersionId=value;}
    @TableField(value="task_bundle_id", updateStrategy=FieldStrategy.ALWAYS)
    private String taskBundleId;
    public String getTaskBundleId() {return taskBundleId;}
    public void setTaskBundleId(String value) {taskBundleId=value;}
    @TableField(value="target_revision", updateStrategy=FieldStrategy.ALWAYS)
    private Long targetRevision;
    public Long getTargetRevision() {return targetRevision;}
    public void setTargetRevision(Long value) {targetRevision=value;}
    @TableField(value="content_hash", updateStrategy=FieldStrategy.ALWAYS)
    private String contentHash;
    public String getContentHash() {return contentHash;}
    public void setContentHash(String value) {contentHash=value;}
    @TableField(value="content_snapshot_json", updateStrategy=FieldStrategy.ALWAYS)
    private String contentSnapshotJson;
    public String getContentSnapshotJson() {return contentSnapshotJson;}
    public void setContentSnapshotJson(String value) {contentSnapshotJson=value;}
    @TableField(value="schedule_preview_id", updateStrategy=FieldStrategy.ALWAYS)
    private String schedulePreviewId;
    public String getSchedulePreviewId() {return schedulePreviewId;}
    public void setSchedulePreviewId(String value) {schedulePreviewId=value;}
    @TableField(value="status", updateStrategy=FieldStrategy.ALWAYS)
    private String status;
    public String getStatus() {return status;}
    public void setStatus(String value) {status=value;}
    @TableField(value="submitted_by", updateStrategy=FieldStrategy.ALWAYS)
    private String submittedBy;
    public String getSubmittedBy() {return submittedBy;}
    public void setSubmittedBy(String value) {submittedBy=value;}
    @TableField(value="submitted_at", updateStrategy=FieldStrategy.ALWAYS)
    private LocalDateTime submittedAt;
    public LocalDateTime getSubmittedAt() {return submittedAt;}
    public void setSubmittedAt(LocalDateTime value) {submittedAt=value;}
    @TableField(value="closed_at", updateStrategy=FieldStrategy.ALWAYS)
    private LocalDateTime closedAt;
    public LocalDateTime getClosedAt() {return closedAt;}
    public void setClosedAt(LocalDateTime value) {closedAt=value;}
    @TableField(value="closed_by", updateStrategy=FieldStrategy.ALWAYS)
    private String closedBy;
    public String getClosedBy() {return closedBy;}
    public void setClosedBy(String value) {closedBy=value;}
    @TableField(value="closed_reason", updateStrategy=FieldStrategy.ALWAYS)
    private String closedReason;
    public String getClosedReason() {return closedReason;}
    public void setClosedReason(String value) {closedReason=value;}
}
