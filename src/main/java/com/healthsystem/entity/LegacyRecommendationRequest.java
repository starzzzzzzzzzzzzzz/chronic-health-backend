package com.healthsystem.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("legacy_recommendation_request")
public class LegacyRecommendationRequest extends ScopedEntity {
    private String requestKey;
    private String requestHash;
    private String generationJobId;
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { requestHash = value; }
    public String getGenerationJobId() { return generationJobId; }
    public void setGenerationJobId(String value) { generationJobId = value; }
}
