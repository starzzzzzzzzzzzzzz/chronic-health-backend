package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("audit_log")
public class AuditLog extends ScopedEntity {
    @TableField(value = "actor_id", updateStrategy = FieldStrategy.ALWAYS)
    private String actorId;
    @TableField(value = "action", updateStrategy = FieldStrategy.ALWAYS)
    private String action;
    @TableField(value = "target_type", updateStrategy = FieldStrategy.ALWAYS)
    private String targetType;
    @TableField(value = "target_id", updateStrategy = FieldStrategy.ALWAYS)
    private String targetId;
    @TableField(value = "before_revision", updateStrategy = FieldStrategy.ALWAYS)
    private Long beforeRevision;
    @TableField(value = "after_revision", updateStrategy = FieldStrategy.ALWAYS)
    private Long afterRevision;
    @TableField(value = "request_id", updateStrategy = FieldStrategy.ALWAYS)
    private String requestId;
    @TableField(value = "change_summary_json", updateStrategy = FieldStrategy.ALWAYS)
    private String changeSummaryJson;
    @TableField(value = "occurred_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime occurredAt;
    public String getActorId() { return actorId; }
    public void setActorId(String value) { this.actorId = value; }
    public String getAction() { return action; }
    public void setAction(String value) { this.action = value; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String value) { this.targetType = value; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String value) { this.targetId = value; }
    public Long getBeforeRevision() { return beforeRevision; }
    public void setBeforeRevision(Long value) { this.beforeRevision = value; }
    public Long getAfterRevision() { return afterRevision; }
    public void setAfterRevision(Long value) { this.afterRevision = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { this.requestId = value; }
    public String getChangeSummaryJson() { return changeSummaryJson; }
    public void setChangeSummaryJson(String value) { this.changeSummaryJson = value; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime value) { this.occurredAt = value; }
}
