package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("task_group")
public class TaskGroup extends ScopedEntity {
 @TableField(value="task_bundle_id",updateStrategy=FieldStrategy.ALWAYS) private String taskBundleId;
 public String getTaskBundleId(){return taskBundleId;} public void setTaskBundleId(String value){taskBundleId=value;}
 @TableField(value="dimension_code",updateStrategy=FieldStrategy.ALWAYS) private String dimensionCode;
 public String getDimensionCode(){return dimensionCode;} public void setDimensionCode(String value){dimensionCode=value;}
 @TableField(value="actor_type",updateStrategy=FieldStrategy.ALWAYS) private String actorType;
 public String getActorType(){return actorType;} public void setActorType(String value){actorType=value;}
 @TableField(value="task_class",updateStrategy=FieldStrategy.ALWAYS) private String taskClass;
 public String getTaskClass(){return taskClass;} public void setTaskClass(String value){taskClass=value;}
 @TableField(value="priority_code",updateStrategy=FieldStrategy.ALWAYS) private String priorityCode;
 public String getPriorityCode(){return priorityCode;} public void setPriorityCode(String value){priorityCode=value;}
 @TableField(value="safety_focus",updateStrategy=FieldStrategy.ALWAYS) private Boolean safetyFocus;
 public Boolean getSafetyFocus(){return safetyFocus;} public void setSafetyFocus(Boolean value){safetyFocus=value;}
 @TableField(value="evaluation_mode",updateStrategy=FieldStrategy.ALWAYS) private String evaluationMode;
 public String getEvaluationMode(){return evaluationMode;} public void setEvaluationMode(String value){evaluationMode=value;}
 @TableField(value="title",updateStrategy=FieldStrategy.ALWAYS) private String title;
 public String getTitle(){return title;} public void setTitle(String value){title=value;}
 @TableField(value="content_json",updateStrategy=FieldStrategy.ALWAYS) private String contentJson;
 public String getContentJson(){return contentJson;} public void setContentJson(String value){contentJson=value;}
 @TableField(value="schedule_rule_json",updateStrategy=FieldStrategy.ALWAYS) private String scheduleRuleJson;
 public String getScheduleRuleJson(){return scheduleRuleJson;} public void setScheduleRuleJson(String value){scheduleRuleJson=value;}
 @TableField(value="sort_order",updateStrategy=FieldStrategy.ALWAYS) private Integer sortOrder;
 public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer value){sortOrder=value;}
}
