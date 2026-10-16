package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("patient")
public class Patient extends ScopedEntity {
    @TableField(value = "updated_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updatedAt;
    @TableField(value = "row_version", updateStrategy = FieldStrategy.ALWAYS)
    private Long rowVersion;
    @TableField(value = "external_system", updateStrategy = FieldStrategy.ALWAYS)
    private String externalSystem;
    @TableField(value = "external_archive_id", updateStrategy = FieldStrategy.ALWAYS)
    private String externalArchiveId;
    @TableField(value = "name", updateStrategy = FieldStrategy.ALWAYS)
    private String name;
    @TableField(value = "sex", updateStrategy = FieldStrategy.ALWAYS)
    private String sex;
    @TableField(value = "birth_date", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate birthDate;
    @TableField(value = "team_id", updateStrategy = FieldStrategy.ALWAYS)
    private String teamId;
    @TableField(value = "status", updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(value = "data_mode", updateStrategy = FieldStrategy.ALWAYS)
    private String dataMode;
    @TableField(value = "profile_revision", updateStrategy = FieldStrategy.ALWAYS)
    private Long profileRevision;
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { this.rowVersion = value; }
    public String getExternalSystem() { return externalSystem; }
    public void setExternalSystem(String value) { this.externalSystem = value; }
    public String getExternalArchiveId() { return externalArchiveId; }
    public void setExternalArchiveId(String value) { this.externalArchiveId = value; }
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public String getSex() { return sex; }
    public void setSex(String value) { this.sex = value; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate value) { this.birthDate = value; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { this.teamId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getDataMode() { return dataMode; }
    public void setDataMode(String value) { this.dataMode = value; }
    public Long getProfileRevision() { return profileRevision; }
    public void setProfileRevision(Long value) { this.profileRevision = value; }
}
