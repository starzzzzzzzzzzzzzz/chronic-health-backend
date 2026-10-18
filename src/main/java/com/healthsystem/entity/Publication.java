package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("publication")
public class Publication extends ScopedEntity {
 @TableField(value="patient_id",updateStrategy=FieldStrategy.ALWAYS) private String patientId;
 public String getPatientId(){return patientId;} public void setPatientId(String value){patientId=value;}
 @TableField(value="task_bundle_id",updateStrategy=FieldStrategy.ALWAYS) private String taskBundleId;
 public String getTaskBundleId(){return taskBundleId;} public void setTaskBundleId(String value){taskBundleId=value;}
 @TableField(value="review_submission_id",updateStrategy=FieldStrategy.ALWAYS) private String reviewSubmissionId;
 public String getReviewSubmissionId(){return reviewSubmissionId;} public void setReviewSubmissionId(String value){reviewSubmissionId=value;}
 @TableField(value="schedule_preview_id",updateStrategy=FieldStrategy.ALWAYS) private String schedulePreviewId;
 public String getSchedulePreviewId(){return schedulePreviewId;} public void setSchedulePreviewId(String value){schedulePreviewId=value;}
 @TableField(value="publication_preview_id",updateStrategy=FieldStrategy.ALWAYS) private String publicationPreviewId;
 public String getPublicationPreviewId(){return publicationPreviewId;} public void setPublicationPreviewId(String value){publicationPreviewId=value;}
 @TableField(value="request_hash",updateStrategy=FieldStrategy.ALWAYS) private String requestHash;
 public String getRequestHash(){return requestHash;} public void setRequestHash(String value){requestHash=value;}
 @TableField(value="effective_at",updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime effectiveAt;
 public LocalDateTime getEffectiveAt(){return effectiveAt;} public void setEffectiveAt(LocalDateTime value){effectiveAt=value;}
 @TableField(value="status",updateStrategy=FieldStrategy.ALWAYS) private String status;
 public String getStatus(){return status;} public void setStatus(String value){status=value;}
 @TableField(value="notification_status",updateStrategy=FieldStrategy.ALWAYS) private String notificationStatus;
 public String getNotificationStatus(){return notificationStatus;} public void setNotificationStatus(String value){notificationStatus=value;}
}
