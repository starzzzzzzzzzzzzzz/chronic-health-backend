package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("schedule_preview")
public class SchedulePreview extends ScopedEntity {
 @TableField(value="task_bundle_id",updateStrategy=FieldStrategy.ALWAYS) private String taskBundleId;
 public String getTaskBundleId(){return taskBundleId;} public void setTaskBundleId(String value){taskBundleId=value;}
 @TableField(value="bundle_revision",updateStrategy=FieldStrategy.ALWAYS) private Long bundleRevision;
 public Long getBundleRevision(){return bundleRevision;} public void setBundleRevision(Long value){bundleRevision=value;}
 @TableField(value="content_hash",updateStrategy=FieldStrategy.ALWAYS) private String contentHash;
 public String getContentHash(){return contentHash;} public void setContentHash(String value){contentHash=value;}
 @TableField(value="preview_hash",updateStrategy=FieldStrategy.ALWAYS) private String previewHash;
 public String getPreviewHash(){return previewHash;} public void setPreviewHash(String value){previewHash=value;}
 @TableField(value="rule_version",updateStrategy=FieldStrategy.ALWAYS) private String ruleVersion;
 public String getRuleVersion(){return ruleVersion;} public void setRuleVersion(String value){ruleVersion=value;}
 @TableField(value="occurrences_json",updateStrategy=FieldStrategy.ALWAYS) private String occurrencesJson;
 public String getOccurrencesJson(){return occurrencesJson;} public void setOccurrencesJson(String value){occurrencesJson=value;}
 @TableField(value="issues_json",updateStrategy=FieldStrategy.ALWAYS) private String issuesJson;
 public String getIssuesJson(){return issuesJson;} public void setIssuesJson(String value){issuesJson=value;}
}
