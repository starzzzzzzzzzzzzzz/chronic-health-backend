package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("condition_confirmation")
public class ConditionConfirmation extends ScopedEntity {
    @TableField(value="plan_version_id", updateStrategy=FieldStrategy.ALWAYS)
    private String planVersionId;
    public String getPlanVersionId() {return planVersionId;}
    public void setPlanVersionId(String value) {planVersionId=value;}
    @TableField(value="task_bundle_id", updateStrategy=FieldStrategy.ALWAYS)
    private String taskBundleId;
    public String getTaskBundleId() {return taskBundleId;}
    public void setTaskBundleId(String value) {taskBundleId=value;}
    @TableField(value="condition_key", updateStrategy=FieldStrategy.ALWAYS)
    private String conditionKey;
    public String getConditionKey() {return conditionKey;}
    public void setConditionKey(String value) {conditionKey=value;}
    @TableField(value="requirement_json", updateStrategy=FieldStrategy.ALWAYS)
    private String requirementJson;
    public String getRequirementJson() {return requirementJson;}
    public void setRequirementJson(String value) {requirementJson=value;}
    @TableField(value="value_json", updateStrategy=FieldStrategy.ALWAYS)
    private String valueJson;
    public String getValueJson() {return valueJson;}
    public void setValueJson(String value) {valueJson=value;}
    @TableField(value="status", updateStrategy=FieldStrategy.ALWAYS)
    private String status;
    public String getStatus() {return status;}
    public void setStatus(String value) {status=value;}
    @TableField(value="source_fact_id", updateStrategy=FieldStrategy.ALWAYS)
    private String sourceFactId;
    public String getSourceFactId() {return sourceFactId;}
    public void setSourceFactId(String value) {sourceFactId=value;}
    @TableField(value="subject_revision", updateStrategy=FieldStrategy.ALWAYS)
    private Long subjectRevision;
    public Long getSubjectRevision() {return subjectRevision;}
    public void setSubjectRevision(Long value) {subjectRevision=value;}
    @TableField(value="binding_hash", updateStrategy=FieldStrategy.ALWAYS)
    private String bindingHash;
    public String getBindingHash() {return bindingHash;}
    public void setBindingHash(String value) {bindingHash=value;}
    @TableField(value="confirmed_by", updateStrategy=FieldStrategy.ALWAYS)
    private String confirmedBy;
    public String getConfirmedBy() {return confirmedBy;}
    public void setConfirmedBy(String value) {confirmedBy=value;}
    @TableField(value="confirmed_at", updateStrategy=FieldStrategy.ALWAYS)
    private LocalDateTime confirmedAt;
    public LocalDateTime getConfirmedAt() {return confirmedAt;}
    public void setConfirmedAt(LocalDateTime value) {confirmedAt=value;}
    @TableField(value="confirmation_note", updateStrategy=FieldStrategy.ALWAYS)
    private String confirmationNote;
    public String getConfirmationNote() {return confirmationNote;}
    public void setConfirmationNote(String value) {confirmationNote=value;}
    @TableField(value="supersedes_confirmation_id", updateStrategy=FieldStrategy.ALWAYS)
    private String supersedesConfirmationId;
    public String getSupersedesConfirmationId() {return supersedesConfirmationId;}
    public void setSupersedesConfirmationId(String value) {supersedesConfirmationId=value;}
}
