package com.healthsystem.vo;
import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
public record ConditionConfirmationVO(String versionId,String rowVersion,String contentRevision,List<Condition> conditions,List<Record> history) {
    public record Condition(String conditionKey,String kind,String itemId,String actorType,JsonNode requirement,String bindingHash,String status,Record record) {}
    public record Record(String id,String conditionKey,JsonNode requirement,JsonNode value,String status,String sourceFactId,String subjectRevision,String bindingHash,String confirmedBy,OffsetDateTime confirmedAt,String confirmationNote,String supersedesConfirmationId,String createdBy,OffsetDateTime createdAt) {}
}
