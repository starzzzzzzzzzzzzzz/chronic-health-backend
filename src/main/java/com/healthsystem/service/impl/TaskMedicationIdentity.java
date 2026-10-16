package com.healthsystem.service.impl;
import tools.jackson.databind.JsonNode;
/** Only literal identifiers from the confirmed order; no free-text parsing or drug-name inference. */
final class TaskMedicationIdentity {
 private TaskMedicationIdentity(){}
 static String of(JsonNode group) {
  var binding=group.path("professionalBinding");
  for(String field:new String[]{"medicationId","drugCode","drugName"}){var value=binding.path(field);if(value.isString()&&!value.asText().isBlank())return field+":"+value.asText();}
  return null;
 }
}
