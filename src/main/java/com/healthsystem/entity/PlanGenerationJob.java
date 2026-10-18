package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("plan_generation_job")
public class PlanGenerationJob extends ScopedEntity {
    private String patientId;
    public String getPatientId() { return patientId; }
    public void setPatientId(String value) { patientId=value; }
    private String planVersionId;
    public String getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(String value) { planVersionId=value; }
    private String inputSnapshotId;
    public String getInputSnapshotId() { return inputSnapshotId; }
    public void setInputSnapshotId(String value) { inputSnapshotId=value; }
    private Long baseRevision;
    public Long getBaseRevision() { return baseRevision; }
    public void setBaseRevision(Long value) { baseRevision=value; }
    private String dataMode;
    public String getDataMode() { return dataMode; }
    public void setDataMode(String value) { dataMode=value; }
    private String idempotencyKey;
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey=value; }
    private String requestHash;
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { requestHash=value; }
    private String retryOfJobId;
    public String getRetryOfJobId() { return retryOfJobId; }
    public void setRetryOfJobId(String value) { retryOfJobId=value; }
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    private String model;
    public String getModel() { return model; }
    public void setModel(String value) { model=value; }
    private String promptVersion;
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String value) { promptVersion=value; }
    private String ruleVersion;
    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String value) { ruleVersion=value; }
    private String systemPrompt;
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String value) { systemPrompt=value; }
    private String modelInputJson;
    public String getModelInputJson() { return modelInputJson; }
    public void setModelInputJson(String value) { modelInputJson=value; }
    private String rawOutput;
    public String getRawOutput() { return rawOutput; }
    public void setRawOutput(String value) { rawOutput=value; }
    private String candidateJson;
    public String getCandidateJson() { return candidateJson; }
    public void setCandidateJson(String value) { candidateJson=value; }
    private String issuesJson;
    public String getIssuesJson() { return issuesJson; }
    public void setIssuesJson(String value) { issuesJson=value; }
    private String usageJson;
    public String getUsageJson() { return usageJson; }
    public void setUsageJson(String value) { usageJson=value; }
    private String providerRequestId;
    public String getProviderRequestId() { return providerRequestId; }
    public void setProviderRequestId(String value) { providerRequestId=value; }
    private String errorCode;
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { errorCode=value; }
    private LocalDateTime startedAt;
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt=value; }
    private LocalDateTime finishedAt;
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime value) { finishedAt=value; }
    private LocalDateTime appliedAt;
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime value) { appliedAt=value; }
}
