package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("task_bundle")
public class TaskBundle extends ScopedEntity {
 @TableField(value="patient_id",updateStrategy=FieldStrategy.ALWAYS) private String patientId;
 public String getPatientId(){return patientId;} public void setPatientId(String value){patientId=value;}
 @TableField(value="plan_version_id",updateStrategy=FieldStrategy.ALWAYS) private String planVersionId;
 public String getPlanVersionId(){return planVersionId;} public void setPlanVersionId(String value){planVersionId=value;}
 @TableField(value="source_review_submission_id",updateStrategy=FieldStrategy.ALWAYS) private String sourceReviewSubmissionId;
 public String getSourceReviewSubmissionId(){return sourceReviewSubmissionId;} public void setSourceReviewSubmissionId(String value){sourceReviewSubmissionId=value;}
 @TableField(value="source_review_decision_id",updateStrategy=FieldStrategy.ALWAYS) private String sourceReviewDecisionId;
 public String getSourceReviewDecisionId(){return sourceReviewDecisionId;} public void setSourceReviewDecisionId(String value){sourceReviewDecisionId=value;}
 @TableField(value="source_revision",updateStrategy=FieldStrategy.ALWAYS) private Long sourceRevision;
 public Long getSourceRevision(){return sourceRevision;} public void setSourceRevision(Long value){sourceRevision=value;}
 @TableField(value="source_plan_hash",updateStrategy=FieldStrategy.ALWAYS) private String sourcePlanHash;
 public String getSourcePlanHash(){return sourcePlanHash;} public void setSourcePlanHash(String value){sourcePlanHash=value;}
 @TableField(value="source_review_hash",updateStrategy=FieldStrategy.ALWAYS) private String sourceReviewHash;
 public String getSourceReviewHash(){return sourceReviewHash;} public void setSourceReviewHash(String value){sourceReviewHash=value;}
 @TableField(value="input_snapshot_id",updateStrategy=FieldStrategy.ALWAYS) private String inputSnapshotId;
 public String getInputSnapshotId(){return inputSnapshotId;} public void setInputSnapshotId(String value){inputSnapshotId=value;}
 @TableField(value="source_snapshot_json",updateStrategy=FieldStrategy.ALWAYS) private String sourceSnapshotJson;
 public String getSourceSnapshotJson(){return sourceSnapshotJson;} public void setSourceSnapshotJson(String value){sourceSnapshotJson=value;}
 @TableField(value="next_cycle_reference_json",updateStrategy=FieldStrategy.ALWAYS) private String nextCycleReferenceJson;
 public String getNextCycleReferenceJson(){return nextCycleReferenceJson;} public void setNextCycleReferenceJson(String value){nextCycleReferenceJson=value;}
 @TableField(value="based_on_bundle_id",updateStrategy=FieldStrategy.ALWAYS) private String basedOnBundleId;
 public String getBasedOnBundleId(){return basedOnBundleId;} public void setBasedOnBundleId(String value){basedOnBundleId=value;}
 @TableField(value="version_no",updateStrategy=FieldStrategy.ALWAYS) private Integer versionNo;
 public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer value){versionNo=value;}
 @TableField(value="row_version",updateStrategy=FieldStrategy.ALWAYS) private Long rowVersion;
 public Long getRowVersion(){return rowVersion;} public void setRowVersion(Long value){rowVersion=value;}
 @TableField(value="content_revision",updateStrategy=FieldStrategy.ALWAYS) private Long contentRevision;
 public Long getContentRevision(){return contentRevision;} public void setContentRevision(Long value){contentRevision=value;}
 @TableField(value="status",updateStrategy=FieldStrategy.ALWAYS) private String status;
 public String getStatus(){return status;} public void setStatus(String value){status=value;}
 @TableField(value="start_date",updateStrategy=FieldStrategy.ALWAYS) private LocalDate startDate;
 public LocalDate getStartDate(){return startDate;} public void setStartDate(LocalDate value){startDate=value;}
 @TableField(value="end_date",updateStrategy=FieldStrategy.ALWAYS) private LocalDate endDate;
 public LocalDate getEndDate(){return endDate;} public void setEndDate(LocalDate value){endDate=value;}
 @TableField(value="zone_id",updateStrategy=FieldStrategy.ALWAYS) private String zoneId;
 public String getZoneId(){return zoneId;} public void setZoneId(String value){zoneId=value;}
 @TableField(value="generation_mode",updateStrategy=FieldStrategy.ALWAYS) private String generationMode;
 public String getGenerationMode(){return generationMode;} public void setGenerationMode(String value){generationMode=value;}
 @TableField(value="content_hash",updateStrategy=FieldStrategy.ALWAYS) private String contentHash;
 public String getContentHash(){return contentHash;} public void setContentHash(String value){contentHash=value;}
 @TableField(value="latest_preview_id",updateStrategy=FieldStrategy.ALWAYS) private String latestPreviewId;
 public String getLatestPreviewId(){return latestPreviewId;} public void setLatestPreviewId(String value){latestPreviewId=value;}
 @TableField(value="updated_at",updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime updatedAt;
 public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime value){updatedAt=value;}
}
