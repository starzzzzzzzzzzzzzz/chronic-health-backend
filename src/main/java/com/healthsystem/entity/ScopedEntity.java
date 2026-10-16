package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
/** Shared persistence columns for organization-scoped business rows. */
public abstract class ScopedEntity {
 @TableId(type=IdType.INPUT) private String id;
 private String orgId;
 private LocalDateTime createdAt;
 private String createdBy;
 public String getId(){return id;} public void setId(String value){id=value;}
 public String getOrgId(){return orgId;} public void setOrgId(String value){orgId=value;}
 public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime value){createdAt=value;}
 public String getCreatedBy(){return createdBy;} public void setCreatedBy(String value){createdBy=value;}
}
