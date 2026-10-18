package com.healthsystem.service.impl;
import com.healthsystem.common.json.JsonValues;
import com.healthsystem.entity.ExecutionTask;
import com.healthsystem.exception.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanDraftValidation.check;
@Component
public class TaskFeedbackPolicy {
 public JsonNode assess(ExecutionTask t,JsonNode body,Instant received) {
  fields(body,"completion","actualExecutedAt","reportedSafety","note","reason","feeling","values");
  var now=LocalDateTime.ofInstant(received,ZoneOffset.UTC);
  if(now.isBefore(t.getOpensAt()))throw BusinessException.conflict("Execution window has not opened");
  if(!now.isBefore(t.getMakeupCutoffAt()))throw BusinessException.conflict("Makeup deadline has passed; do not perform or backdate a missed task");
  String completion=string(body,"completion",false,16);if(completion==null)completion="COMPLETED";
  check(Set.of("COMPLETED","PARTIAL","NOT_DONE","STOPPED").contains(completion),"Invalid completion");
  String actual=string(body,"actualExecutedAt",false,40),timing="UNKNOWN";
  if(actual!=null){var executed=TaskPublicationService.time(body,"actualExecutedAt");check(!executed.isAfter(now),"Actual execution time cannot be in the future");timing=executed.isBefore(t.getOpensAt())?"EARLY":!executed.isBefore(t.getClosesAt())?"LATE":"ON_TIME";}
  for(String name:List.of("note","reason","feeling"))string(body,name,false,20000);
  var safety=body.get("reportedSafety");check(safety==null||safety.isNull()||safety.isBoolean(),"reportedSafety must be boolean or null");
  if(body.hasNonNull("values")){var values=body.path("values");check(values.isObject()&&values.size()<=20,"Optional values must be an object with up to 20 entries");for(var entry:values.properties())check(entry.getKey().length()<=64&&(entry.getValue().isNumber()||entry.getValue().isBoolean()||entry.getValue().isNull()||(entry.getValue().isString()&&entry.getValue().asText().length()<=1000)),"Invalid optional value");}
  boolean signal="STOPPED".equals(completion)||(safety!=null&&safety.asBoolean(false));
  String safetyStatus=signal?"REQUIRES_REVIEW":safety==null||safety.isNull()?"UNKNOWN":"NONE_REPORTED";
  var assessment=new LinkedHashMap<String,Object>();assessment.put("completion",completion);assessment.put("completionRatio",switch(completion){case "COMPLETED"->100;case "PARTIAL"->50;case "NOT_DONE"->0;default->null;});
  assessment.put("executionTiming",timing);assessment.put("submissionSource",now.isBefore(t.getNormalCutoffAt())?"NORMAL":"MAKEUP");assessment.put("safetyStatus",safetyStatus);assessment.put("qualityStatus","NOT_APPLICABLE");assessment.put("qualityUnavailableReason","COMPLETION_ONLY");
  var suggested=JsonValues.read(t.getContentSnapshotJson()).path("group").path("feedbackSchema").path("fields");boolean full=!suggested.isEmpty();for(var field:suggested)if(!body.hasNonNull(field.asText()))full=false;
  assessment.put("feedbackCompleteness",full?"COMPLETE":"LIMITED");assessment.put("selfReported",true);return JsonValues.tree(assessment);
 }
}
