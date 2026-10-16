package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("task_feedback")
public class TaskFeedback extends ScopedEntity {
 @TableField(value="execution_task_id",updateStrategy=FieldStrategy.ALWAYS) private String executionTaskId;
 public String getExecutionTaskId(){return executionTaskId;} public void setExecutionTaskId(String value){executionTaskId=value;}
 @TableField(value="previous_feedback_id",updateStrategy=FieldStrategy.ALWAYS) private String previousFeedbackId;
 public String getPreviousFeedbackId(){return previousFeedbackId;} public void setPreviousFeedbackId(String value){previousFeedbackId=value;}
 @TableField(value="version_no",updateStrategy=FieldStrategy.ALWAYS) private Integer versionNo;
 public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer value){versionNo=value;}
 @TableField(value="idempotency_key",updateStrategy=FieldStrategy.ALWAYS) private String idempotencyKey;
 public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String value){idempotencyKey=value;}
 @TableField(value="request_hash",updateStrategy=FieldStrategy.ALWAYS) private String requestHash;
 public String getRequestHash(){return requestHash;} public void setRequestHash(String value){requestHash=value;}
 @TableField(value="body_json",updateStrategy=FieldStrategy.ALWAYS) private String bodyJson;
 public String getBodyJson(){return bodyJson;} public void setBodyJson(String value){bodyJson=value;}
 @TableField(value="assessment_json",updateStrategy=FieldStrategy.ALWAYS) private String assessmentJson;
 public String getAssessmentJson(){return assessmentJson;} public void setAssessmentJson(String value){assessmentJson=value;}
 @TableField(value="received_at",updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime receivedAt;
 public LocalDateTime getReceivedAt(){return receivedAt;} public void setReceivedAt(LocalDateTime value){receivedAt=value;}
 @TableField(value="submitted_by",updateStrategy=FieldStrategy.ALWAYS) private String submittedBy;
 public String getSubmittedBy(){return submittedBy;} public void setSubmittedBy(String value){submittedBy=value;}
}
