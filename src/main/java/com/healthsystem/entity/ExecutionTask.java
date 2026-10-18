package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
@TableName("execution_task")
public class ExecutionTask extends ScopedEntity {
 @TableField(value="patient_id",updateStrategy=FieldStrategy.ALWAYS) private String patientId;
 public String getPatientId(){return patientId;} public void setPatientId(String value){patientId=value;}
 @TableField(value="publication_id",updateStrategy=FieldStrategy.ALWAYS) private String publicationId;
 public String getPublicationId(){return publicationId;} public void setPublicationId(String value){publicationId=value;}
 @TableField(value="task_bundle_id",updateStrategy=FieldStrategy.ALWAYS) private String taskBundleId;
 public String getTaskBundleId(){return taskBundleId;} public void setTaskBundleId(String value){taskBundleId=value;}
 @TableField(value="occurrence_key",updateStrategy=FieldStrategy.ALWAYS) private String occurrenceKey;
 public String getOccurrenceKey(){return occurrenceKey;} public void setOccurrenceKey(String value){occurrenceKey=value;}
 @TableField(value="group_id",updateStrategy=FieldStrategy.ALWAYS) private String groupId;
 public String getGroupId(){return groupId;} public void setGroupId(String value){groupId=value;}
 @TableField(value="variant_id",updateStrategy=FieldStrategy.ALWAYS) private String variantId;
 public String getVariantId(){return variantId;} public void setVariantId(String value){variantId=value;}
 @TableField(value="dimension_code",updateStrategy=FieldStrategy.ALWAYS) private String dimensionCode;
 public String getDimensionCode(){return dimensionCode;} public void setDimensionCode(String value){dimensionCode=value;}
 @TableField(value="execution_date",updateStrategy=FieldStrategy.ALWAYS) private LocalDate executionDate;
 public LocalDate getExecutionDate(){return executionDate;} public void setExecutionDate(LocalDate value){executionDate=value;}
 @TableField(value="zone_id",updateStrategy=FieldStrategy.ALWAYS) private String zoneId;
 public String getZoneId(){return zoneId;} public void setZoneId(String value){zoneId=value;}
 @TableField(value="slot_key",updateStrategy=FieldStrategy.ALWAYS) private String slotKey;
 public String getSlotKey(){return slotKey;} public void setSlotKey(String value){slotKey=value;}
 @TableField(value="planned_at",updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime plannedAt;
 public LocalDateTime getPlannedAt(){return plannedAt;} public void setPlannedAt(LocalDateTime value){plannedAt=value;}
 @TableField(value="opens_at",updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime opensAt;
 public LocalDateTime getOpensAt(){return opensAt;} public void setOpensAt(LocalDateTime value){opensAt=value;}
 @TableField(value="closes_at",updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime closesAt;
 public LocalDateTime getClosesAt(){return closesAt;} public void setClosesAt(LocalDateTime value){closesAt=value;}
 @TableField(value="normal_cutoff_at",updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime normalCutoffAt;
 public LocalDateTime getNormalCutoffAt(){return normalCutoffAt;} public void setNormalCutoffAt(LocalDateTime value){normalCutoffAt=value;}
 @TableField(value="makeup_cutoff_at",updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime makeupCutoffAt;
 public LocalDateTime getMakeupCutoffAt(){return makeupCutoffAt;} public void setMakeupCutoffAt(LocalDateTime value){makeupCutoffAt=value;}
 @TableField(value="content_snapshot_json",updateStrategy=FieldStrategy.ALWAYS) private String contentSnapshotJson;
 public String getContentSnapshotJson(){return contentSnapshotJson;} public void setContentSnapshotJson(String value){contentSnapshotJson=value;}
 @TableField(value="task_class",updateStrategy=FieldStrategy.ALWAYS) private String taskClass;
 public String getTaskClass(){return taskClass;} public void setTaskClass(String value){taskClass=value;}
 @TableField(value="priority_code",updateStrategy=FieldStrategy.ALWAYS) private String priorityCode;
 public String getPriorityCode(){return priorityCode;} public void setPriorityCode(String value){priorityCode=value;}
 @TableField(value="safety_focus",updateStrategy=FieldStrategy.ALWAYS) private Boolean safetyFocus;
 public Boolean getSafetyFocus(){return safetyFocus;} public void setSafetyFocus(Boolean value){safetyFocus=value;}
 @TableField(value="cancelled_at",updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime cancelledAt;
 public LocalDateTime getCancelledAt(){return cancelledAt;} public void setCancelledAt(LocalDateTime value){cancelledAt=value;}
 @TableField(value="replaced_by_publication_id",updateStrategy=FieldStrategy.ALWAYS) private String replacedByPublicationId;
 public String getReplacedByPublicationId(){return replacedByPublicationId;} public void setReplacedByPublicationId(String value){replacedByPublicationId=value;}
}
