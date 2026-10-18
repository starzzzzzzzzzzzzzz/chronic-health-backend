package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("task_template")
public class TaskTemplate extends ScopedEntity {
 @TableField(value="template_code",updateStrategy=FieldStrategy.ALWAYS) private String templateCode;
 public String getTemplateCode(){return templateCode;} public void setTemplateCode(String value){templateCode=value;}
 @TableField(value="dimension_code",updateStrategy=FieldStrategy.ALWAYS) private String dimensionCode;
 public String getDimensionCode(){return dimensionCode;} public void setDimensionCode(String value){dimensionCode=value;}
 @TableField(value="version_no",updateStrategy=FieldStrategy.ALWAYS) private Integer versionNo;
 public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer value){versionNo=value;}
 @TableField(value="review_status",updateStrategy=FieldStrategy.ALWAYS) private String reviewStatus;
 public String getReviewStatus(){return reviewStatus;} public void setReviewStatus(String value){reviewStatus=value;}
 @TableField(value="data_mode",updateStrategy=FieldStrategy.ALWAYS) private String dataMode;
 public String getDataMode(){return dataMode;} public void setDataMode(String value){dataMode=value;}
 @TableField(value="content_json",updateStrategy=FieldStrategy.ALWAYS) private String contentJson;
 public String getContentJson(){return contentJson;} public void setContentJson(String value){contentJson=value;}
 @TableField(value="content_hash",updateStrategy=FieldStrategy.ALWAYS) private String contentHash;
 public String getContentHash(){return contentHash;} public void setContentHash(String value){contentHash=value;}
}
