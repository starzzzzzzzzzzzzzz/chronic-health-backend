package com.healthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.*;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import com.healthsystem.service.*;
import com.healthsystem.vo.CarePlanDraftVO;
import com.healthsystem.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import static com.healthsystem.service.impl.PlanDraftValidation.*;

@Service
public class CarePlanServiceImpl implements CarePlanService {
 private final CarePlanMapper plans;private final CarePlanVersionMapper versions;private final HealthInputSnapshotMapper snapshots;private final AuditLogMapper audits;private final PatientService patients;private final PlanDraftStore store;
 public CarePlanServiceImpl(CarePlanMapper plans,CarePlanVersionMapper versions,HealthInputSnapshotMapper snapshots,AuditLogMapper audits,PatientService patients,PlanDraftStore store){this.plans=plans;this.versions=versions;this.snapshots=snapshots;this.audits=audits;this.patients=patients;this.store=store;}
 private static final Set<String> PATCH_FIELDS=Set.of("expectedRevision","title","startDate","endDate","zoneId","primaryGoal","goalBasis","supplementalRequirements","changeReason","modules","managementFocuses","dimensions","items");
 @Override @Transactional
 public CarePlanDraftVO create(BusinessPrincipal principal,CreateCarePlanRequest request){
  period(request.startDate(),request.endDate(),request.zoneId());text(request.title(),true,200);text(request.primaryGoal(),false,1000);
  var patient=patients.require(principal,request.patientId(),"PLAN_EDIT",true);
  if(revision(request.expectedProfileRevision())!=patient.getProfileRevision())throw BusinessException.conflict("Patient profile changed; reload before creating a draft");
  var input=patients.healthSummary(principal,patient.getId());LocalDateTime now=LocalDateTime.now(Clock.systemUTC());
  HealthInputSnapshot snapshot=stamp(new HealthInputSnapshot(),principal,now);snapshot.setPatientId(patient.getId());snapshot.setStage("PLAN");snapshot.setProfileRevision(patient.getProfileRevision());snapshot.setCapturedAt(now);snapshot.setSchemaVersion("patient-input-v1");snapshot.setPayloadJson(JsonValues.write(input));snapshot.setPayloadHash(CanonicalJson.hash(JsonValues.tree(input)));snapshots.insert(snapshot);
  CarePlan plan=stamp(new CarePlan(),principal,now);plan.setPatientId(patient.getId());plan.setPlanNo("CP-"+UUID.randomUUID());plan.setStatus("OPEN");plan.setRowVersion(0L);plan.setUpdatedAt(now);plans.insert(plan);
  CarePlanVersion v=stamp(new CarePlanVersion(),principal,now);v.setPlanId(plan.getId());v.setVersionNo(1);v.setTitle(request.title());v.setStartDate(request.startDate());v.setEndDate(request.endDate());v.setZoneId(request.zoneId());v.setPrimaryGoal(request.primaryGoal());v.setInputSnapshotId(snapshot.getId());v.setStatus("DRAFT");v.setRowVersion(0L);v.setContentRevision(0L);v.setUpdatedAt(now);
  var content=new PlanDraftStore.Content(List.of(),List.of(),List.of(),List.of());v.setContentHash(PlanDraftHash.hash(v,content,snapshot));versions.insert(v);
  plan.setCurrentVersionId(v.getId());plans.updateById(plan);audit(principal,v,"DRAFT_CREATED",null,Set.of("draft"));
  return view(plan,v,content,snapshot);
 }
 @Override @Transactional(readOnly=true)
 public CarePlanDraftVO get(BusinessPrincipal principal,String id){
  var v=findVersion(principal,id);var plan=findPlan(principal,v.getPlanId());patients.require(principal,plan.getPatientId(),"PATIENT_READ",false);
  return view(plan,v,store.load(v),snapshot(principal,v));
 }
 @Override @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public CarePlanDraftVO update(BusinessPrincipal principal,String id,UpdateCarePlanRequest request){
  JsonNode patch=request.changes();for(String field:patch.propertyNames())check(PATCH_FIELDS.contains(field),"Unknown patch field");
  var first=findVersion(principal,id);var plan=findPlan(principal,first.getPlanId());
  patients.require(principal,plan.getPatientId(),"PLAN_EDIT",true);patients.require(principal,plan.getPatientId(),"PATIENT_READ",false);
  var v=plans.lockVersion(principal.orgId(),id);if(v==null)throw new BusinessException(404,"Plan version not found");
  long expected=revision(requiredString(patch,"expectedRevision"));
  if(expected!=v.getRowVersion())throw BusinessException.conflict("Draft changed; reload before saving");
  if(!"DRAFT".equals(v.getStatus()))throw BusinessException.conflict("Only a draft may be edited");
  var input=snapshot(principal,v);var previous=store.load(v);String oldReason=v.getChangeReason();
  if(patch.has("title"))v.setTitle(readText(patch,"title",true,200));
  if(patch.has("primaryGoal"))v.setPrimaryGoal(readText(patch,"primaryGoal",false,1000));
  if(patch.has("supplementalRequirements"))v.setSupplementalRequirements(readText(patch,"supplementalRequirements",false,20000));
  if(patch.has("changeReason"))v.setChangeReason(readText(patch,"changeReason",false,1000));
  if(patch.has("startDate"))v.setStartDate(date(patch,"startDate"));if(patch.has("endDate"))v.setEndDate(date(patch,"endDate"));if(patch.has("zoneId"))v.setZoneId(readText(patch,"zoneId",true,64));period(v.getStartDate(),v.getEndDate(),v.getZoneId());
  if(patch.has("goalBasis")){
   var goal=patch.get("goalBasis");check(goal.isNull()||goal.isObject(),"Invalid goal basis");
   if(goal.has("factIds")){check(goal.get("factIds").isArray(),"Invalid goal fact references");var ids=new HashSet<String>();for(var f:JsonValues.read(input.getPayloadJson()).path("facts"))ids.add(f.path("id").asText());for(var f:goal.get("factIds"))check(f.isString()&&ids.contains(f.asText()),"Goal fact not in input snapshot");}
   v.setGoalBasisJson(goal.isNull()?null:JsonValues.write(goal));
  }
  var content=normalize(previous,patch,JsonValues.read(input.getPayloadJson()));String oldHash=v.getContentHash(),hash=PlanDraftHash.hash(v,content,input);
  boolean contentChanged=!hash.equals(oldHash);
  if(!contentChanged&&Objects.equals(oldReason,v.getChangeReason()))return view(plan,v,previous,input);
  LocalDateTime now=LocalDateTime.now(Clock.systemUTC());if(contentChanged)store.replace(principal,v,content,now);
  long before=v.getRowVersion();v.setRowVersion(Math.addExact(before,1));if(contentChanged)v.setContentRevision(Math.addExact(v.getContentRevision(),1));v.setUpdatedAt(now);v.setContentHash(hash);
  if(versions.updateById(v)!=1)throw BusinessException.conflict("Draft changed");
  audit(principal,v,"DRAFT_UPDATED",before,new TreeSet<>(patch.propertyNames()));
  return view(plan,v,content,input);
 }
 private CarePlanVersion findVersion(BusinessPrincipal p,String id){var v=versions.selectOne(new QueryWrapper<CarePlanVersion>().eq("id",id).eq("org_id",p.orgId()));if(v==null)throw new BusinessException(404,"Plan version not found");return v;}
 private CarePlan findPlan(BusinessPrincipal p,String id){var v=plans.selectOne(new QueryWrapper<CarePlan>().eq("id",id).eq("org_id",p.orgId()));if(v==null)throw new BusinessException(404,"Plan not found");return v;}
 private HealthInputSnapshot snapshot(BusinessPrincipal p,CarePlanVersion v){var s=snapshots.selectOne(new QueryWrapper<HealthInputSnapshot>().eq("id",v.getInputSnapshotId()).eq("org_id",p.orgId()));if(s==null)throw new IllegalStateException("Missing plan input snapshot");return s;}
 private CarePlanDraftVO view(CarePlan plan,CarePlanVersion v,PlanDraftStore.Content c,HealthInputSnapshot s){return new CarePlanDraftVO(plan.getId(),v.getId(),v.getVersionNo(),plan.getPatientId(),v.getStatus(),String.valueOf(v.getRowVersion()),String.valueOf(v.getContentRevision()),v.getTitle(),v.getStartDate(),v.getEndDate(),v.getZoneId(),v.getPrimaryGoal(),JsonValues.read(v.getGoalBasisJson()),v.getSupplementalRequirements(),v.getChangeReason(),s.getId(),JsonValues.read(s.getPayloadJson()),v.getContentHash(),c.modules(),c.managementFocuses(),c.dimensions(),c.items(),v.getUpdatedAt().atOffset(ZoneOffset.UTC));}

 private void audit(BusinessPrincipal p,CarePlanVersion v,String action,Long before,Set<String> changed){var a=stamp(new AuditLog(),p,LocalDateTime.now(Clock.systemUTC()));a.setActorId(p.subjectId());a.setAction(action);a.setTargetType("CARE_PLAN_VERSION");a.setTargetId(v.getId());a.setBeforeRevision(before);a.setAfterRevision(v.getRowVersion());a.setRequestId(Optional.ofNullable(MDC.get("requestId")).orElseGet(()->UUID.randomUUID().toString()));a.setChangeSummaryJson(JsonValues.write(Map.of("fields",changed,"contentHash",v.getContentHash())));a.setOccurredAt(a.getCreatedAt());audits.insert(a);}
 private static <T extends ScopedEntity>T stamp(T e,BusinessPrincipal p,LocalDateTime now){e.setId(UUID.randomUUID().toString());e.setOrgId(p.orgId());e.setCreatedBy(p.subjectId());e.setCreatedAt(now);return e;}
 private static String requiredString(JsonNode node,String name){check(node.hasNonNull(name)&&node.get(name).isString(),"Missing or invalid "+name);return node.get(name).asText();}
 private static String readText(JsonNode node,String name,boolean required,int max){var value=node.get(name);check(value!=null&&(value.isString()||(!required&&value.isNull())),"Invalid "+name);return text(value.isNull()?null:value.asText(),required,max);}
 private static LocalDate date(JsonNode node,String name){try{return LocalDate.parse(requiredString(node,name));}catch(java.time.format.DateTimeParseException e){throw BusinessException.badRequest("Invalid date");}}
}
