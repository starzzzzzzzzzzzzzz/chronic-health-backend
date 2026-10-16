package com.healthsystem.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import com.healthsystem.exception.BusinessException;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanReviewSupport.*;
@Service
public class TaskFeedbackService {
 private final ExecutionTaskService executions;private final TaskFeedbackPolicy policy;private final TaskFeedbackMapper feedback;private final TaskResultMapper results;private final TaskSafetyEventMapper safety;private final AuditLogMapper audits;private final Clock clock;
 public TaskFeedbackService(ExecutionTaskService e,TaskFeedbackPolicy p,TaskFeedbackMapper f,TaskResultMapper r,TaskSafetyEventMapper s,AuditLogMapper a,Clock c){executions=e;policy=p;feedback=f;results=r;safety=s;audits=a;clock=c;}
 public record Written(JsonNode data,boolean created){}
 @Transactional(isolation=Isolation.READ_COMMITTED) public Written checkIn(BusinessPrincipal p,String task,String key,ReviewWorkflowRequest request){return write(p,task,null,key,request.body());}
 @Transactional(isolation=Isolation.READ_COMMITTED) public Written amend(BusinessPrincipal p,String id,String key,ReviewWorkflowRequest request){var previous=find(p,id);return write(p,previous.getExecutionTaskId(),previous,key,request.body());}
 private Written write(BusinessPrincipal p,String taskId,TaskFeedback previous,String key,JsonNode body) {
  key=TaskPublicationService.key(key);fields(body,previous==null?new String[]{"completion","actualExecutedAt","reportedSafety","note","reason","feeling","values"}:new String[]{"expectedRevision","completion","actualExecutedAt","reportedSafety","note","reason","feeling","values"});
  var task=executions.require(p,taskId,"TASK_FEEDBACK",true);String hash=CanonicalJson.hash(JsonValues.tree(Map.of("targetId",previous==null?taskId:previous.getId(),"operation",previous==null?"MAIN":"AMEND","body",body)));
  var replay=feedback.selectOne(new QueryWrapper<TaskFeedback>().eq("org_id",p.orgId()).eq("idempotency_key",key));
  if(replay!=null){if(!replay.getExecutionTaskId().equals(taskId)||!replay.getRequestHash().equals(hash))throw BusinessException.conflict("Idempotency key has a different feedback request");return new Written(view(replay),false);}
  var result=results.selectOne(new QueryWrapper<TaskResult>().eq("org_id",p.orgId()).eq("execution_task_id",taskId));
  if(previous==null&&result!=null)throw BusinessException.conflict("Task already has a primary check-in; append an amendment");
  if(previous!=null&&(result==null||!result.getCurrentFeedbackId().equals(previous.getId())||result.getRowVersion()!=expected(body)))throw BusinessException.conflict("Feedback changed; use the latest feedback and result revision");
  var effective=(ObjectNode)(previous==null?body.deepCopy():JsonValues.read(previous.getBodyJson()).deepCopy());if(previous!=null)for(var entry:body.properties())if(!"expectedRevision".equals(entry.getKey()))effective.set(entry.getKey(),entry.getValue());
  Instant received=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);var assessment=policy.assess(task,effective,received);
  var row=stamp(new TaskFeedback(),p);row.setExecutionTaskId(taskId);row.setPreviousFeedbackId(previous==null?null:previous.getId());row.setVersionNo(previous==null?1:previous.getVersionNo()+1);row.setIdempotencyKey(key);row.setRequestHash(hash);row.setBodyJson(JsonValues.write(effective));row.setAssessmentJson(JsonValues.write(assessment));row.setReceivedAt(LocalDateTime.ofInstant(received,ZoneOffset.UTC));row.setSubmittedBy(p.subjectId());feedback.insert(row);
  if("REQUIRES_REVIEW".equals(assessment.path("safetyStatus").asText())){var event=stamp(new TaskSafetyEvent(),p);event.setExecutionTaskId(taskId);event.setFeedbackId(row.getId());event.setStatus("OPEN");event.setSignalJson(JsonValues.write(Map.of("completion",assessment.path("completion").asText(),"reportedSafety",effective.path("reportedSafety").asBoolean(false))));safety.insert(event);}
  var aggregate=(ObjectNode)assessment.deepCopy();if(safety.selectCount(new QueryWrapper<TaskSafetyEvent>().eq("org_id",p.orgId()).eq("execution_task_id",taskId).eq("status","OPEN"))>0)aggregate.put("safetyStatus","REQUIRES_REVIEW");
  if(result==null){result=stamp(new TaskResult(),p);result.setExecutionTaskId(taskId);result.setRowVersion(0L);}else result.setRowVersion(Math.addExact(result.getRowVersion(),1));
  result.setCurrentFeedbackId(row.getId());result.setAssessmentJson(JsonValues.write(aggregate));result.setUpdatedAt(row.getReceivedAt());if(previous==null)results.insert(result);else results.updateById(result);
  var a=stamp(new AuditLog(),p);a.setActorId(p.subjectId());a.setAction(previous==null?"TASK_CHECKED_IN":"TASK_FEEDBACK_AMENDED");a.setTargetType("EXECUTION_TASK");a.setTargetId(taskId);a.setBeforeRevision(previous==null?null:result.getRowVersion()-1);a.setAfterRevision(result.getRowVersion());a.setRequestId(Optional.ofNullable(MDC.get("requestId")).orElseGet(()->UUID.randomUUID().toString()));a.setChangeSummaryJson(JsonValues.write(Map.of("feedbackId",row.getId(),"version",row.getVersionNo())));a.setOccurredAt(row.getReceivedAt());audits.insert(a);
  return new Written(view(row),true);
 }
 @Transactional(readOnly=true) public List<JsonNode> history(BusinessPrincipal p,String id){executions.require(p,id,"TASK_READ",false);return feedback.selectList(new QueryWrapper<TaskFeedback>().eq("org_id",p.orgId()).eq("execution_task_id",id).orderByAsc("version_no")).stream().map(this::view).toList();}
 @Transactional(readOnly=true) public JsonNode get(BusinessPrincipal p,String id){var row=find(p,id);executions.require(p,row.getExecutionTaskId(),"TASK_READ",false);return view(row);}
 private TaskFeedback find(BusinessPrincipal p,String id){var row=feedback.selectOne(new QueryWrapper<TaskFeedback>().eq("org_id",p.orgId()).eq("id",id));if(row==null)throw new BusinessException(404,"Check-in not found");return row;}
 private JsonNode view(TaskFeedback f){var m=new LinkedHashMap<String,Object>();m.put("id",f.getId());m.put("executionTaskId",f.getExecutionTaskId());m.put("previousFeedbackId",f.getPreviousFeedbackId());m.put("versionNo",f.getVersionNo());m.put("resultRevision",String.valueOf(f.getVersionNo()-1));m.put("body",JsonValues.read(f.getBodyJson()));m.put("assessment",JsonValues.read(f.getAssessmentJson()));m.put("receivedAt",utc(f.getReceivedAt()));m.put("submittedBy",f.getSubmittedBy());return JsonValues.tree(m);}
}
