package com.healthsystem.dto;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.healthsystem.exception.BusinessException;
import tools.jackson.databind.JsonNode;
/** Strict workflow bodies retain presence and reject server-owned properties. */
public final class ReviewWorkflowRequest {
    private final JsonNode body;
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public ReviewWorkflowRequest(JsonNode body) {
        if(body==null || !body.isObject()) throw BusinessException.badRequest("Request must be an object");
        this.body=body;
    }
    public JsonNode body(){return body;}
}
