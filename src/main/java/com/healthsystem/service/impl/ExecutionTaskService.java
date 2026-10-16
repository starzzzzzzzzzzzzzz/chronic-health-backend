package com.healthsystem.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.JsonValues;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import com.healthsystem.service.PatientService;
import com.healthsystem.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import static com.healthsystem.service.impl.PlanReviewSupport.utc;
@Service
public class ExecutionTaskService {
 final ExecutionTaskMapper tasks;final PublicationMapper publications;private final PatientService patients;final Clock clock;private final TaskResultMapper results;private final ZoneId defaultZone;
 public ExecutionTaskService(ExecutionTaskMapper t,PublicationMapper p,PatientService pa,TaskResultMapper r,Clock c,@Value("${health.workflow.zone-id:Asia/Shanghai}") String zone){tasks=t;publications=p;patients=pa;clock=c;results=r;defaultZone=ZoneId.of(zone);}
 @Transactional(readOnly=true) public List<JsonNode> list(BusinessPrincipal p,String patient,LocalDate date) {
  patients.require(p,patient,"TASK_READ",false);LocalDate day=date==null?LocalDate.now(clock.withZone(defaultZone)):date;
  var now=LocalDateTime.now(clock);return tasks.selectList(new QueryWrapper<ExecutionTask>().eq("org_id",p.orgId()).eq("patient_id",patient).eq("execution_date",day).orderByAsc("planned_at","priority_code","id")).stream().filter(t->visible(t,now)).map(this::view).toList();
 }
 @Transactional(readOnly=true) public JsonNode get(BusinessPrincipal p,String id){return view(require(p,id,"TASK_READ",false));}
 public ExecutionTask require(BusinessPrincipal p,String id,String capability,boolean lock) {
  var task=tasks.selectOne(new QueryWrapper<ExecutionTask>().eq("org_id",p.orgId()).eq("id",id));if(task==null)throw new BusinessException(404,"Execution task not found");
  patients.require(p,task.getPatientId(),capability,lock);if(lock)task=tasks.lock(p.orgId(),id);
  if(!visible(task,LocalDateTime.now(clock)))throw new BusinessException(404,"Execution task is not active");return task;
 }
 private boolean visible(ExecutionTask t,LocalDateTime now){var pub=publications.selectById(t.getPublicationId());return pub!=null&&"PUBLISHED".equals(pub.getStatus())&&!pub.getEffectiveAt().isAfter(now)&&(t.getCancelledAt()==null||t.getCancelledAt().isAfter(now));}
 public JsonNode view(ExecutionTask t) {
  var m=new LinkedHashMap<String,Object>();m.put("id",t.getId());m.put("patientId",t.getPatientId());m.put("publicationId",t.getPublicationId());m.put("taskBundleId",t.getTaskBundleId());m.put("occurrenceKey",t.getOccurrenceKey());m.put("dimensionCode",t.getDimensionCode());m.put("executionDate",t.getExecutionDate());m.put("zoneId",t.getZoneId());m.put("slotKey",t.getSlotKey());m.put("plannedAt",utc(t.getPlannedAt()));m.put("opensAt",utc(t.getOpensAt()));m.put("closesAt",utc(t.getClosesAt()));m.put("normalCutoffAt",utc(t.getNormalCutoffAt()));m.put("makeupCutoffAt",utc(t.getMakeupCutoffAt()));m.put("contentSnapshot",JsonValues.read(t.getContentSnapshotJson()));m.put("taskClass",t.getTaskClass());m.put("priorityCode",t.getPriorityCode());m.put("safetyFocus",t.getSafetyFocus());m.put("scheduledCancellationAt",utc(t.getCancelledAt()));
  var result=results.selectOne(new QueryWrapper<TaskResult>().eq("org_id",t.getOrgId()).eq("execution_task_id",t.getId()));
  m.put("currentResult",result==null?null:Map.of("rowVersion",String.valueOf(result.getRowVersion()),"currentFeedbackId",result.getCurrentFeedbackId(),"assessment",JsonValues.read(result.getAssessmentJson()),"updatedAt",utc(result.getUpdatedAt())));return JsonValues.tree(m);
 }
}
