package com.healthsystem.config;

import io.swagger.v3.oas.models.media.*;
import java.util.*;

/** Explicit response shapes for nullable workflow state and Jackson 3 JSON objects. */
final class WorkflowResponseSchemas {
    private WorkflowResponseSchemas() {}
    static void apply(Map<String, Schema> schemas) {
        for (var entry : Map.of(
                "CarePlanDraftVO", List.of("goalBasis", "inputSnapshot"),
                "TaskBundleVO", List.of("inputSnapshot", "nextCycleReference"),
                "ReviewSubmissionVO", List.of("snapshot")).entrySet()) {
            var model=schemas.get(entry.getKey());
            if(model!=null) for(String field:entry.getValue())
                model.addProperty(field,new ObjectSchema().nullable("goalBasis".equals(field)));
        }
        for(var entry:Map.of(
                "ConditionConfirmationEntry",List.of("value"),
                "ApiResponseMapStringObject",List.of("data"),
                "CheckInRequest",List.of("values"),
                "FeedbackAmendmentRequest",List.of("values"),
                "TaskGroupView",List.of("template","professionalBinding","feedbackSchema"),
                "Variant",List.of("parameters")).entrySet()) {
            var model=schemas.get(entry.getKey());
            if(model!=null) for(String field:entry.getValue()) {
                var property=(Schema)model.getProperties().get(field);
                if(property!=null)property.setAdditionalProperties(true);
            }
        }
        var generation=schemas.get("PlanGenerationJobVO");
        if(generation!=null)for(String field:List.of("candidate","issues","usage"))
            generation.addProperty(field,new ObjectSchema().additionalProperties(true).nullable(true));
        nullable(schemas,"PlanGenerationJobVO","errorCode","retryOfJobId","startedAt","finishedAt","appliedAt");
        nullable(schemas,"FactVO","unit","sourceRef","observedAt","confidence","confirmedBy","confirmedAt");
        nullable(schemas,"CarePlanDraftVO","primaryGoal","goalBasis","supplementalRequirements","changeReason");
        nullable(schemas,"Dimension","decisionReason");
        nullable(schemas,"Basis","note");
        nullable(schemas,"Focus","adjustmentReason","contributionBps");
        nullable(schemas,"Condition","record");
        nullable(schemas,"Record","value","sourceFactId","confirmedBy","confirmedAt","confirmationNote","supersedesConfirmationId");
        nullable(schemas,"ReviewSubmissionVO","planVersionId","closedBy","closedAt","closedReason","decision","taskBundleId","schedulePreviewId");
        nullable(schemas,"Decision","comment");
        nullable(schemas,"TaskBundleVO","basedOnBundleId","latestPreviewId");
        nullable(schemas,"TaskGroupView","professionalBinding","confirmationNote","schedule");
        nullable(schemas,"ExecutionView","makeupCutoffAt","scheduledCancellationAt","currentResult");
        nullable(schemas,"Occurrence","makeupCutoffAt");
        nullable(schemas,"FeedbackView","previousFeedbackId");
        nullable(schemas,"Assessment","completionRatio");
    }
    private static void nullable(Map<String,Schema> schemas,String name,String... fields) {
        var model=schemas.get(name);
        if(model==null||model.getProperties()==null)return;
        for(String field:fields) {
            var property=(Schema)model.getProperties().get(field);
            if(property!=null) {
                if(property.get$ref()!=null) {
                    // OpenAPI 3.0 ignores siblings of $ref; represent null as a separate union branch.
                    property.setNullable(null);
                    model.addProperty(field,new ComposedSchema().oneOf(List.of(property,
                            new StringSchema().nullable(true)._enum(Collections.singletonList(null)))));
                } else property.setNullable(true);
            }
        }
    }
}
