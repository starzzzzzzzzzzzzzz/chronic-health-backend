package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("care_plan_item")
public class CarePlanItem extends ScopedEntity {
    @TableField(value = "plan_version_id", updateStrategy = FieldStrategy.ALWAYS)
    private String planVersionId;
    @TableField(value = "dimension_id", updateStrategy = FieldStrategy.ALWAYS)
    private String dimensionId;
    @TableField(value = "title", updateStrategy = FieldStrategy.ALWAYS)
    private String title;
    @TableField(value = "content", updateStrategy = FieldStrategy.ALWAYS)
    private String content;
    @TableField(value = "execution_goal", updateStrategy = FieldStrategy.ALWAYS)
    private Boolean executionGoal;
    @TableField(value = "actor_type", updateStrategy = FieldStrategy.ALWAYS)
    private String actorType;
    @TableField(value = "contraindications_json", updateStrategy = FieldStrategy.ALWAYS)
    private String contraindicationsJson;
    @TableField(value = "professional_parameters_json", updateStrategy = FieldStrategy.ALWAYS)
    private String professionalParametersJson;
    @TableField(value = "references_json", updateStrategy = FieldStrategy.ALWAYS)
    private String referencesJson;
    @TableField(value = "is_selected", updateStrategy = FieldStrategy.ALWAYS)
    private Boolean isSelected;
    @TableField(value = "sort_order", updateStrategy = FieldStrategy.ALWAYS)
    private Integer sortOrder;
    public String getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(String value) { this.planVersionId = value; }
    public String getDimensionId() { return dimensionId; }
    public void setDimensionId(String value) { this.dimensionId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getContent() { return content; }
    public void setContent(String value) { this.content = value; }
    public Boolean getExecutionGoal() { return executionGoal; }
    public void setExecutionGoal(Boolean value) { this.executionGoal = value; }
    public String getActorType() { return actorType; }
    public void setActorType(String value) { this.actorType = value; }
    public String getContraindicationsJson() { return contraindicationsJson; }
    public void setContraindicationsJson(String value) { this.contraindicationsJson = value; }
    public String getProfessionalParametersJson() { return professionalParametersJson; }
    public void setProfessionalParametersJson(String value) { this.professionalParametersJson = value; }
    public String getReferencesJson() { return referencesJson; }
    public void setReferencesJson(String value) { this.referencesJson = value; }
    public Boolean getIsSelected() { return isSelected; }
    public void setIsSelected(Boolean value) { this.isSelected = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { this.sortOrder = value; }
}
