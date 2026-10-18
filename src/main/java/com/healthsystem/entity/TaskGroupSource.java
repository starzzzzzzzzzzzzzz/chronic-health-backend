package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("task_group_source")
public class TaskGroupSource extends ScopedEntity {
 @TableField(value="task_group_id",updateStrategy=FieldStrategy.ALWAYS) private String taskGroupId;
 public String getTaskGroupId(){return taskGroupId;} public void setTaskGroupId(String value){taskGroupId=value;}
 @TableField(value="plan_item_id",updateStrategy=FieldStrategy.ALWAYS) private String planItemId;
 public String getPlanItemId(){return planItemId;} public void setPlanItemId(String value){planItemId=value;}
 @TableField(value="fact_id",updateStrategy=FieldStrategy.ALWAYS) private String factId;
 public String getFactId(){return factId;} public void setFactId(String value){factId=value;}
 @TableField(value="module_code",updateStrategy=FieldStrategy.ALWAYS) private String moduleCode;
 public String getModuleCode(){return moduleCode;} public void setModuleCode(String value){moduleCode=value;}
}
