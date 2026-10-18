package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("task_result")
public class TaskResult extends ScopedEntity {
 @TableField(value="execution_task_id",updateStrategy=FieldStrategy.ALWAYS) private String executionTaskId;
 public String getExecutionTaskId(){return executionTaskId;} public void setExecutionTaskId(String value){executionTaskId=value;}
 @TableField(value="current_feedback_id",updateStrategy=FieldStrategy.ALWAYS) private String currentFeedbackId;
 public String getCurrentFeedbackId(){return currentFeedbackId;} public void setCurrentFeedbackId(String value){currentFeedbackId=value;}
 @TableField(value="row_version",updateStrategy=FieldStrategy.ALWAYS) private Long rowVersion;
 public Long getRowVersion(){return rowVersion;} public void setRowVersion(Long value){rowVersion=value;}
 @TableField(value="assessment_json",updateStrategy=FieldStrategy.ALWAYS) private String assessmentJson;
 public String getAssessmentJson(){return assessmentJson;} public void setAssessmentJson(String value){assessmentJson=value;}
 @TableField(value="updated_at",updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime updatedAt;
 public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime value){updatedAt=value;}
}
