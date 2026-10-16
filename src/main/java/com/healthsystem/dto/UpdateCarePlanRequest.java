package com.healthsystem.dto;
import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.healthsystem.exception.BusinessException;
/** Presence-preserving PATCH DTO: absent fields differ from explicitly supplied null. */
public final class UpdateCarePlanRequest {
 private final ObjectNode changes;
 @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
 public UpdateCarePlanRequest(JsonNode node) {
  if(node==null||!node.isObject()) throw BusinessException.badRequest("Patch must be an object");
  this.changes=(ObjectNode)node;
 }
 public ObjectNode changes() {return changes;}
}
