package com.healthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.*;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import com.healthsystem.service.*;
import com.healthsystem.vo.*;
import com.healthsystem.exception.BusinessException;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.healthsystem.service.impl.PlanDraftValidation.*;

@Service
public class PlanGenerationService {
    private final PlanGenerationJobMapper jobs;
    private final CarePlanMapper plans;
    private final CarePlanVersionMapper versions;
    private final HealthInputSnapshotMapper snapshots;
    private final PatientService patients;
    private final CarePlanService drafts;
    private final AiPlanPolicy policy;
    private final ManagementFocusPolicy management;
    private final Environment env;
    public PlanGenerationService(PlanGenerationJobMapper j,CarePlanMapper p,CarePlanVersionMapper v,
        HealthInputSnapshotMapper s,PatientService patients,CarePlanService drafts,AiPlanPolicy policy,ManagementFocusPolicy management,Environment env) {
        jobs=j;plans=p;versions=v;snapshots=s;this.patients=patients;this.drafts=drafts;this.policy=policy;this.management=management;this.env=env;
    }
    public record Submitted(PlanGenerationJobVO job,boolean created) {}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Submitted submit(BusinessPrincipal p,String versionId,String key,GeneratePlanRequest request) {
        if(!"DEMO".equals(p.dataMode()))throw new BusinessException(403,"AI generation is limited to DEMO data");
        if(!env.getProperty("health.ai.enabled",Boolean.class,false)||env.getProperty("health.ai.api-key","").isBlank())
            throw new BusinessException(503,"AI generation is not configured");
        check(key!=null&&key.matches("[A-Za-z0-9._:-]{1,128}"),"Invalid idempotency key");
        var view=drafts.get(p,versionId);var patient=patients.require(p,view.patientId(),"PLAN_EDIT",true);
        var version=plans.lockVersion(p.orgId(),versionId);
        String hash=CanonicalJson.hash(JsonValues.tree(Map.of("versionId",versionId,"request",request)));
        var replay=jobs.selectOne(new QueryWrapper<PlanGenerationJob>().eq("org_id",p.orgId()).eq("created_by",p.subjectId()).eq("idempotency_key",key));
        if(replay!=null){if(!replay.getRequestHash().equals(hash))throw BusinessException.conflict("Idempotency key has another request");return new Submitted(view(replay),false);}
        if(!"DRAFT".equals(version.getStatus())||version.getRowVersion()!=revision(request.expectedRevision()))throw BusinessException.conflict("Draft changed; reload before generating");
        if(patient.getProfileRevision()!=revision(request.expectedProfileRevision()))throw BusinessException.conflict("Patient profile changed; reload before generating");
        if(jobs.selectCount(new QueryWrapper<PlanGenerationJob>().eq("org_id",p.orgId()).eq("plan_version_id",versionId).in("status","PENDING","RUNNING"))>0)
            throw BusinessException.conflict("A generation is already active for this draft");
        if(jobs.selectCount(new QueryWrapper<PlanGenerationJob>().eq("org_id",p.orgId()).in("status","PENDING","RUNNING"))>=20)
            throw new BusinessException(429,"Generation queue is full");
        if(request.retryOfJobId()!=null) {
            var old=require(p,request.retryOfJobId());
            check(old.getPlanVersionId().equals(versionId)&&Set.of("FAILED","REJECTED").contains(old.getStatus()),"Retry source must be a failed generation of this draft");
        }
        var summary=(ObjectNode)JsonValues.tree(patients.healthSummary(p,patient.getId()));
        check(!summary.path("facts").isEmpty(),"Add patient facts before generating a plan");
        if(!view.managementFocuses().isEmpty()&&(request.confirmedManagement()==null||request.confirmedManagement().isNull()))
            throw BusinessException.conflict("Existing management choices require explicit source-bound confirmation before regenerating");
        summary.set("managementContext",management.bind(p,summary,request.confirmedManagement()));
        var input=policy.input(summary,version.getSupplementalRequirements());
        input.put("currentGoal",Objects.toString(version.getPrimaryGoal(),""));
        input.put("startDate",version.getStartDate().toString());input.put("endDate",version.getEndDate().toString());
        check(input.toString().length()<=50_000&&input.path("facts").size()<=200,"Patient input exceeds generation limits");
        LocalDateTime now=LocalDateTime.now(Clock.systemUTC());
        var snapshot=stamp(new HealthInputSnapshot(),p,now);snapshot.setPatientId(patient.getId());snapshot.setStage("PLAN");
        snapshot.setProfileRevision(patient.getProfileRevision());snapshot.setCapturedAt(now);snapshot.setSchemaVersion("patient-input-v1");
        snapshot.setPayloadJson(JsonValues.write(summary));snapshot.setPayloadHash(CanonicalJson.hash(summary));snapshots.insert(snapshot);
        var job=stamp(new PlanGenerationJob(),p,now);job.setPatientId(patient.getId());job.setPlanVersionId(versionId);job.setInputSnapshotId(snapshot.getId());
        job.setBaseRevision(version.getRowVersion());job.setDataMode(p.dataMode());job.setIdempotencyKey(key);job.setRequestHash(hash);
        job.setRetryOfJobId(request.retryOfJobId());job.setStatus("PENDING");job.setModel(env.getProperty("health.ai.model","deepseek-flash"));
        job.setPromptVersion("plan-system-v5");job.setRuleVersion(AiPlanPolicy.VERSION);job.setSystemPrompt(systemPrompt());job.setModelInputJson(JsonValues.write(input));
        jobs.insert(job);return new Submitted(view(job),true);
    }
    @Transactional(readOnly=true) public PlanGenerationJobVO get(BusinessPrincipal p,String id){return view(require(p,id));}
    @Transactional(readOnly=true) public List<PlanGenerationJobVO> list(BusinessPrincipal p,String versionId) {
        var draft=drafts.get(p,versionId);patients.require(p,draft.patientId(),"PLAN_EDIT",false);
        return jobs.selectList(new QueryWrapper<PlanGenerationJob>().eq("org_id",p.orgId()).eq("plan_version_id",versionId).orderByDesc("created_at","id").last("LIMIT 20")).stream().map(this::view).toList();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public CarePlanDraftVO apply(BusinessPrincipal p,String jobId,ApplyGeneratedPlanRequest request) {
        var first=require(p,jobId);patients.require(p,first.getPatientId(),"PLAN_EDIT",true);
        var version=plans.lockVersion(p.orgId(),first.getPlanVersionId());
        var job=jobs.selectOne(new QueryWrapper<PlanGenerationJob>().eq("org_id",p.orgId()).eq("id",jobId).last("FOR UPDATE"));
        long expected=revision(request.expectedRevision());
        if("APPLIED".equals(job.getStatus()))return drafts.get(p,version.getId());
        if(!"SUCCEEDED".equals(job.getStatus()))throw BusinessException.conflict("Only a validated candidate can be adopted");
        if(!"DRAFT".equals(version.getStatus())||version.getRowVersion()!=expected||expected!=job.getBaseRevision())
            throw BusinessException.conflict("Draft changed after generation; preserve edits and regenerate");
        var frozen=JsonValues.read(snapshots.selectById(job.getInputSnapshotId()).getPayloadJson()).path("managementContext");
        if(!frozen.path("focuses").isEmpty()&&!frozen.path("sourceHash").asText().equals(
                management.sourceHash(p.orgId(),JsonValues.tree(patients.healthSummary(p,job.getPatientId())))))
            throw BusinessException.conflict("Management source changed after generation; evaluate and confirm again");
        var patch=(ObjectNode)JsonValues.read(job.getCandidateJson()).deepCopy();patch.put("expectedRevision",String.valueOf(expected));
        ((ObjectNode)patch.path("goalBasis")).put("generationJobId",jobId);
        patch.put("changeReason","采用 AI 候选草稿，尚待人工审核");
        // Same transaction as the existing draft validation, child writes, revision and audit.
        version.setInputSnapshotId(job.getInputSnapshotId());versions.updateById(version);
        var updated=drafts.update(p,version.getId(),new UpdateCarePlanRequest(patch));
        jobs.update(null,new UpdateWrapper<PlanGenerationJob>().eq("id",jobId).eq("status","SUCCEEDED").set("status","APPLIED").set("applied_at",LocalDateTime.now(Clock.systemUTC())));
        return updated;
    }
    public PlanGenerationJob require(BusinessPrincipal p,String id) {
        var job=jobs.selectOne(new QueryWrapper<PlanGenerationJob>().eq("org_id",p.orgId()).eq("data_mode",p.dataMode()).eq("id",id));
        if(job==null)throw new BusinessException(404,"Generation job not found");
        patients.require(p,job.getPatientId(),"PATIENT_READ",false);patients.require(p,job.getPatientId(),"PLAN_EDIT",false);return job;
    }
    private PlanGenerationJobVO view(PlanGenerationJob j) {
        return new PlanGenerationJobVO(j.getId(),j.getPlanVersionId(),j.getStatus(),String.valueOf(j.getBaseRevision()),j.getInputSnapshotId(),j.getModel(),j.getPromptVersion(),j.getRuleVersion(),true,
            JsonValues.read(j.getCandidateJson()),JsonValues.read(j.getIssuesJson()),JsonValues.read(j.getUsageJson()),j.getErrorCode(),j.getRetryOfJobId(),utc(j.getCreatedAt()),utc(j.getStartedAt()),utc(j.getFinishedAt()),utc(j.getAppliedAt()));
    }
    private static OffsetDateTime utc(LocalDateTime t){return t==null?null:t.atOffset(ZoneOffset.UTC);}
    private static String systemPrompt(){try{return new ClassPathResource("ai/plan-system-v5.txt").getContentAsString(StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("AI prompt unavailable");}}
    private static <T extends ScopedEntity>T stamp(T row,BusinessPrincipal p,LocalDateTime time){row.setId(UUID.randomUUID().toString());row.setOrgId(p.orgId());row.setCreatedBy(p.subjectId());row.setCreatedAt(time);return row;}
}
