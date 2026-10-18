package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("publication_preview")
public class PublicationPreview extends ScopedEntity {
 @TableField(value="task_bundle_id",updateStrategy=FieldStrategy.ALWAYS) private String taskBundleId;
 public String getTaskBundleId(){return taskBundleId;} public void setTaskBundleId(String value){taskBundleId=value;}
 @TableField(value="bundle_revision",updateStrategy=FieldStrategy.ALWAYS) private Long bundleRevision;
 public Long getBundleRevision(){return bundleRevision;} public void setBundleRevision(Long value){bundleRevision=value;}
 @TableField(value="intent_json",updateStrategy=FieldStrategy.ALWAYS) private String intentJson;
 public String getIntentJson(){return intentJson;} public void setIntentJson(String value){intentJson=value;}
 @TableField(value="manifest_json",updateStrategy=FieldStrategy.ALWAYS) private String manifestJson;
 public String getManifestJson(){return manifestJson;} public void setManifestJson(String value){manifestJson=value;}
 @TableField(value="preview_hash",updateStrategy=FieldStrategy.ALWAYS) private String previewHash;
 public String getPreviewHash(){return previewHash;} public void setPreviewHash(String value){previewHash=value;}
}
