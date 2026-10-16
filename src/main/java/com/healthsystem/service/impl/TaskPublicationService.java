package com.healthsystem.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import com.healthsystem.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanDraftValidation.check;
import static com.healthsystem.service.impl.PlanReviewSupport.*;

@Service
public class TaskPublicationService {
 private final TaskBundleStore store;private final TaskSourcePolicy source;private final TaskScheduleService schedules;
 private final ReviewSubmissionMapper reviews;private final ReviewDecisionMapper decisions;private final CarePlanVersionMapper versions;
 private final PublicationPreviewMapper previews;private final PublicationMapper publications;private final PublicationRequestMapper requests;
 private final ExecutionTaskMapper tasks;private final OutboxEventMapper outbox;private final com.healthsystem.service.PatientService patients;
 public TaskPublicationService(TaskBundleStore s,TaskSourcePolicy so,TaskScheduleService sc,ReviewSubmissionMapper r,ReviewDecisionMapper d,CarePlanVersionMapper v,PublicationPreviewMapper pr,PublicationMapper p,PublicationRequestMapper req,ExecutionTaskMapper t,OutboxEventMapper o,com.healthsystem.service.PatientService pa){store=s;source=so;schedules=sc;reviews=r;decisions=d;versions=v;previews=pr;publications=p;requests=req;tasks=t;outbox=o;patients=pa;}
 public record Published(JsonNode data,boolean created){}
 @Transactional(isolation=Isolation.READ_COMMITTED) public JsonNode preview(BusinessPrincipal p,String id,ReviewWorkflowRequest request) {
  var body=request.body();fields(body,"expectedRevision","reviewSubmissionId","schedulePreviewId","effectiveAt","replacementScope");
  var c=store.context(p,id,"TASK_PUBLISH",true);var schedule=validate(p,c,body);var manifest=manifest(c,schedule,body);
  var row=stamp(new PublicationPreview(),p);row.setTaskBundleId(id);row.setBundleRevision(c.bundle().getRowVersion());row.setIntentJson(CanonicalJson.text(body));row.setManifestJson(JsonValues.write(manifest));row.setPreviewHash(previewHash(row));previews.insert(row);return previewView(row);
 }
 @Transactional(isolation=Isolation.READ_COMMITTED) public Published publish(BusinessPrincipal p,String id,String key,ReviewWorkflowRequest request) {
  key=key(key);var body=request.body();fields(body,"expectedRevision","reviewSubmissionId","schedulePreviewId","effectiveAt","replacementScope","publicationPreviewId");expected(body);
  String hash=CanonicalJson.hash(body);var c=store.context(p,id,"TASK_PUBLISH",true);
  var retry=requests.selectOne(new QueryWrapper<PublicationRequest>().eq("org_id",p.orgId()).eq("idempotency_key",key));
  if(retry!=null){var original=publications.selectById(retry.getPublicationId());if(!retry.getRequestHash().equals(hash)||!original.getTaskBundleId().equals(id))throw BusinessException.conflict("Idempotency key has a different publication request");return new Published(view(original),false);}
  var existing=publications.selectOne(new QueryWrapper<Publication>().eq("org_id",p.orgId()).eq("task_bundle_id",id));
  if(existing!=null){if(!existing.getRequestHash().equals(hash))throw BusinessException.conflict("Task bundle was already published with a different request");remember(p,key,hash,existing.getId());return new Published(view(existing),false);}
  var schedule=validate(p,c,body);String previewId=string(body,"publicationPreviewId",true,36);
  var preview=previews.selectOne(new QueryWrapper<PublicationPreview>().eq("org_id",p.orgId()).eq("id",previewId).eq("task_bundle_id",id));
  var intent=(tools.jackson.databind.node.ObjectNode)body.deepCopy();intent.remove("publicationPreviewId");
  var current=manifest(c,schedule,body);
  if(preview==null||preview.getBundleRevision()!=c.bundle().getRowVersion().longValue()||!CanonicalJson.text(intent).equals(CanonicalJson.text(JsonValues.read(preview.getIntentJson())))||!previewHash(preview).equals(preview.getPreviewHash())||!CanonicalJson.text(JsonValues.tree(current)).equals(CanonicalJson.text(JsonValues.read(preview.getManifestJson()))))throw BusinessException.conflict("Publication preview is stale; inspect a new replacement manifest");
  var row=stamp(new Publication(),p);row.setPatientId(c.bundle().getPatientId());row.setTaskBundleId(id);row.setReviewSubmissionId(string(body,"reviewSubmissionId",true,36));row.setSchedulePreviewId(schedule.getId());row.setPublicationPreviewId(previewId);row.setRequestHash(hash);row.setEffectiveAt(time(body,"effectiveAt"));row.setStatus("PUBLISHED");row.setNotificationStatus("NOT_CONFIGURED");publications.insert(row);
  var included=new HashSet<>(current.createOccurrenceKeys());
  for(var n:JsonValues.read(schedule.getOccurrencesJson()))if(included.contains(n.path("occurrenceKey").asText())) {
   var t=stamp(new ExecutionTask(),p);t.setPatientId(row.getPatientId());t.setPublicationId(row.getId());t.setTaskBundleId(id);t.setOccurrenceKey(n.path("occurrenceKey").asText());t.setGroupId(n.path("groupId").asText());t.setVariantId(n.path("variantId").asText());t.setDimensionCode(n.path("dimensionCode").asText());t.setExecutionDate(LocalDate.parse(n.path("executionDate").asText()));t.setZoneId(n.path("zoneId").asText());t.setSlotKey(n.path("slotKey").asText());
   t.setPlannedAt(time(n,"plannedAt"));t.setOpensAt(time(n,"opensAt"));t.setClosesAt(time(n,"closesAt"));t.setNormalCutoffAt(time(n,"normalCutoffAt"));t.setMakeupCutoffAt(time(n,"makeupCutoffAt"));t.setContentSnapshotJson(JsonValues.write(n.path("contentSnapshot")));t.setTaskClass(n.path("taskClass").asText());t.setPriorityCode(n.path("priorityCode").asText());t.setSafetyFocus(n.path("safetyFocus").asBoolean());tasks.insert(t);
  }
  for(String taskId:current.replaceTaskIds()){var old=tasks.lock(p.orgId(),taskId);old.setCancelledAt(row.getEffectiveAt());old.setReplacedByPublicationId(row.getId());tasks.updateById(old);}
  var event=stamp(new OutboxEvent(),p);event.setPublicationId(row.getId());event.setEventType("TASKS_PUBLISHED");event.setStatus("NOT_CONFIGURED");event.setPayloadJson(JsonValues.write(Map.of("publicationId",row.getId(),"taskCount",included.size())));outbox.insert(event);
  remember(p,key,hash,row.getId());store.advance(p,c.bundle(),"PUBLISHED",false,"TASKS_PUBLISHED");return new Published(view(row),true);
 }
 @Transactional(readOnly=true) public JsonNode get(BusinessPrincipal p,String id) {
  var row=publications.selectOne(new QueryWrapper<Publication>().eq("org_id",p.orgId()).eq("id",id));if(row==null)throw new BusinessException(404,"Publication not found");patients.require(p,row.getPatientId(),"TASK_READ",false);return view(row);
 }
 @Transactional(readOnly=true) public JsonNode getPreview(BusinessPrincipal p,String bundle,String id) {
  store.context(p,bundle,"TASK_READ",false);var row=previews.selectOne(new QueryWrapper<PublicationPreview>().eq("org_id",p.orgId()).eq("task_bundle_id",bundle).eq("id",id));if(row==null)throw new BusinessException(404,"Publication preview not found");return previewView(row);
 }
 private SchedulePreview validate(BusinessPrincipal p,TaskBundleStore.Context c,JsonNode body) {
  var b=c.bundle();store.requireRevision(b,expected(body),false);if(!"APPROVED".equals(b.getStatus()))throw BusinessException.conflict("Task bundle is not approved");source.validate(b,c.source());
  var schedule=schedules.requireCurrent(p,b,string(body,"schedulePreviewId",true,36));var s=reviews.lockSubmission(p.orgId(),string(body,"reviewSubmissionId",true,36));
  if(s==null||!"TASK".equals(s.getStage())||!b.getId().equals(s.getTaskBundleId())||!"APPROVED".equals(s.getStatus())||s.getTargetRevision()!=b.getContentRevision().longValue()||!schedule.getId().equals(s.getSchedulePreviewId())||!TaskReviewService.reviewHash(b,schedule).equals(s.getContentHash()))throw BusinessException.conflict("Approved task review does not match publication");
  var d=decisions.selectOne(new QueryWrapper<ReviewDecision>().eq("org_id",p.orgId()).eq("submission_id",s.getId()).eq("decision","APPROVE"));if(d==null)throw BusinessException.conflict("Task approval decision is missing");
  String scope=string(body,"replacementScope",true,16);check(Set.of("NONE","ALL_FUTURE").contains(scope),"Explicit replacement scope required");time(body,"effectiveAt");return schedule;
 }
 private record Manifest(List<String> createOccurrenceKeys,List<String> replaceTaskIds,List<String> retainTaskIds){}
 private Manifest manifest(TaskBundleStore.Context c,SchedulePreview schedule,JsonNode body) {
  var b=c.bundle();var effective=time(body,"effectiveAt");boolean replace="ALL_FUTURE".equals(body.path("replacementScope").asText());
  var old=tasks.selectList(new QueryWrapper<ExecutionTask>().eq("org_id",b.getOrgId()).eq("patient_id",b.getPatientId()).orderByAsc("id"));var replaceIds=new ArrayList<String>();var retainIds=new ArrayList<String>();var retainedSlots=new HashSet<String>();
  for(var t:old) {
   var oldBundle=store.bundles.selectById(t.getTaskBundleId());var oldVersion=versions.selectById(oldBundle.getPlanVersionId());if(!oldVersion.getPlanId().equals(c.source().plan().getId()))continue;
   var oldPublication=publications.selectById(t.getPublicationId());
   if(oldPublication.getEffectiveAt().isAfter(effective))throw BusinessException.conflict("Publication boundary cannot precede an existing version");
   if(t.getCancelledAt()!=null&&!t.getCancelledAt().isAfter(effective))continue;
   // Feedback existence is checked under the patient parent lock; a later check-in uses the same lock.
   boolean hasFeedback=hasFeedback(t);
   if(replace&&t.getOpensAt().isAfter(effective)&&!hasFeedback)replaceIds.add(t.getId());
   else {retainIds.add(t.getId());retainedSlots.add(businessSlot(JsonValues.read(t.getContentSnapshotJson()),t.getExecutionDate().toString(),t.getSlotKey()));}
  }
  if(!replace && !retainIds.isEmpty())throw BusinessException.conflict("Existing tasks require explicit ALL_FUTURE replacement scope");
  var create=new ArrayList<String>();for(var n:JsonValues.read(schedule.getOccurrencesJson()))if(!time(n,"opensAt").isBefore(effective)&&!retainedSlots.contains(businessSlot(n.path("contentSnapshot"),n.path("executionDate").asText(),n.path("slotKey").asText())))create.add(n.path("occurrenceKey").asText());
  check(!create.isEmpty(),"No new occurrences at the effective boundary");return new Manifest(List.copyOf(create),List.copyOf(replaceIds),List.copyOf(retainIds));
 }
 // Kept in SQL so V7 can run before the feedback migration; V8 adds its indexed result lookup.
 private boolean hasFeedback(ExecutionTask t){return false;}
 private static String businessSlot(JsonNode snapshot,String date,String slot){return CanonicalJson.text(snapshot.path("group").path("sourcePlanItemIds"))+":"+date+":"+slot;}
 private static String previewHash(PublicationPreview p){return CanonicalJson.hash(JsonValues.tree(Map.of("taskBundleId",p.getTaskBundleId(),"bundleRevision",String.valueOf(p.getBundleRevision()),"intent",JsonValues.read(p.getIntentJson()),"manifest",JsonValues.read(p.getManifestJson()))));}
 private JsonNode previewView(PublicationPreview p){var result=(tools.jackson.databind.node.ObjectNode)JsonValues.read(p.getIntentJson()).deepCopy();result.put("id",p.getId()).put("taskBundleId",p.getTaskBundleId()).put("previewHash",p.getPreviewHash());for(var e:JsonValues.read(p.getManifestJson()).properties())result.set(e.getKey(),e.getValue());return result;}
 private JsonNode view(Publication p){return JsonValues.tree(Map.of("id",p.getId(),"taskBundleId",p.getTaskBundleId(),"patientId",p.getPatientId(),"reviewSubmissionId",p.getReviewSubmissionId(),"schedulePreviewId",p.getSchedulePreviewId(),"publicationPreviewId",p.getPublicationPreviewId(),"effectiveAt",utc(p.getEffectiveAt()),"status",p.getStatus(),"notificationStatus",p.getNotificationStatus(),"taskCount",tasks.selectCount(new QueryWrapper<ExecutionTask>().eq("org_id",p.getOrgId()).eq("publication_id",p.getId()))));}
 private void remember(BusinessPrincipal p,String key,String hash,String id){var row=stamp(new PublicationRequest(),p);row.setPublicationId(id);row.setIdempotencyKey(key);row.setRequestHash(hash);requests.insert(row);}
 public static String key(String key){check(key!=null&&key.matches("[A-Za-z0-9._:-]{1,128}"),"Idempotency-Key must contain 1..128 safe ASCII characters");return key;}
 public static LocalDateTime time(JsonNode n,String field){try {return LocalDateTime.ofInstant(Instant.parse(string(n,field,true,40)),ZoneOffset.UTC);}catch(DateTimeException e){throw BusinessException.badRequest("Invalid UTC instant: "+field);}}
}
