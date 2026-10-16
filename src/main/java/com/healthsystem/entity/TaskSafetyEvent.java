package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("task_safety_event")
public class TaskSafetyEvent extends ScopedEntity {
 @TableField(value="execution_task_id",updateStrategy=FieldStrategy.ALWAYS) private String executionTaskId;
 public String getExecutionTaskId(){return executionTaskId;} public void setExecutionTaskId(String value){executionTaskId=value;}
 @TableField(value="feedback_id",updateStrategy=FieldStrategy.ALWAYS) private String feedbackId;
 public String getFeedbackId(){return feedbackId;} public void setFeedbackId(String value){feedbackId=value;}
 @TableField(value="status",updateStrategy=FieldStrategy.ALWAYS) private String status;
 public String getStatus(){return status;} public void setStatus(String value){status=value;}
 @TableField(value="signal_json",updateStrategy=FieldStrategy.ALWAYS) private String signalJson;
 public String getSignalJson(){return signalJson;} public void setSignalJson(String value){signalJson=value;}
}
