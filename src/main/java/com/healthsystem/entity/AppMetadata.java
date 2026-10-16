package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 框架元数据，对应 Flyway V1 已有表。 */
@TableName("app_metadata")
public class AppMetadata {
    @TableId(value = "metadata_key", type = IdType.INPUT)
    private String metadataKey;
    private String metadataValue;
    private LocalDateTime createdAt;

    public String getMetadataKey() { return metadataKey; }
    public void setMetadataKey(String metadataKey) { this.metadataKey = metadataKey; }
    public String getMetadataValue() { return metadataValue; }
    public void setMetadataValue(String metadataValue) { this.metadataValue = metadataValue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
