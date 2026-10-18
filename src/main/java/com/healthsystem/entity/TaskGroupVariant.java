package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("task_group_variant")
public class TaskGroupVariant extends ScopedEntity {
 @TableField(value="task_group_id",updateStrategy=FieldStrategy.ALWAYS) private String taskGroupId;
 public String getTaskGroupId(){return taskGroupId;} public void setTaskGroupId(String value){taskGroupId=value;}
 @TableField(value="variant_key",updateStrategy=FieldStrategy.ALWAYS) private String variantKey;
 public String getVariantKey(){return variantKey;} public void setVariantKey(String value){variantKey=value;}
 @TableField(value="title",updateStrategy=FieldStrategy.ALWAYS) private String title;
 public String getTitle(){return title;} public void setTitle(String value){title=value;}
 @TableField(value="content",updateStrategy=FieldStrategy.ALWAYS) private String content;
 public String getContent(){return content;} public void setContent(String value){content=value;}
 @TableField(value="parameters_json",updateStrategy=FieldStrategy.ALWAYS) private String parametersJson;
 public String getParametersJson(){return parametersJson;} public void setParametersJson(String value){parametersJson=value;}
 @TableField(value="sort_order",updateStrategy=FieldStrategy.ALWAYS) private Integer sortOrder;
 public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer value){sortOrder=value;}
}
