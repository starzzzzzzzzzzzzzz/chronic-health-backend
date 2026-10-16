package com.healthsystem.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import com.healthsystem.service.PatientService;
import com.healthsystem.vo.*;
import com.healthsystem.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.healthsystem.service.impl.PlanReviewSupport.*;

@Component
public class TaskBundleStore {
    public record Context(TaskBundle bundle,PlanReviewSupport.Context source) {}
    final TaskBundleMapper bundles;final TaskGroupMapper groupRows;final TaskGroupVariantMapper variants;final TaskGroupSourceMapper sources;
    final HealthInputSnapshotMapper snapshots;final SchedulePreviewMapper previews;
    private final PlanReviewSupport plans;private final PatientService patients;private final AuditLogMapper audits;
    private final TaskTemplateCatalog templates;
    public TaskBundleStore(TaskBundleMapper b,TaskGroupMapper g,TaskGroupVariantMapper v,TaskGroupSourceMapper s,HealthInputSnapshotMapper i,
            SchedulePreviewMapper pr,PlanReviewSupport pl,PatientService p,AuditLogMapper a,TaskTemplateCatalog t){bundles=b;groupRows=g;variants=v;sources=s;snapshots=i;previews=pr;plans=pl;patients=p;audits=a;templates=t;}
    public Context context(BusinessPrincipal p,String id,String capability,boolean lock) {
        var b=bundles.selectOne(new QueryWrapper<TaskBundle>().eq("org_id",p.orgId()).eq("id",id));
        if(b==null)throw new BusinessException(404,"Task bundle not found");
        var c=plans.context(p,b.getPlanVersionId(),"PATIENT_READ",lock);
        patients.require(p,b.getPatientId(),capability,false);
        if(lock)b=bundles.lock(p.orgId(),id);
        return new Context(b,c);
    }
    public List<TaskGroup> groupRows(TaskBundle b){return groupRows.selectList(new QueryWrapper<TaskGroup>().eq("org_id",b.getOrgId()).eq("task_bundle_id",b.getId()).orderByAsc("sort_order","id"));}
    public List<JsonNode> groups(TaskBundle b) {
        var result=new ArrayList<JsonNode>();
        for(var row:groupRows(b)) {
            var n=(ObjectNode)JsonValues.read(row.getContentJson()).deepCopy();
            n.put("id",row.getId()).put("dimensionCode",row.getDimensionCode()).put("actorType",row.getActorType()).put("taskClass",row.getTaskClass())
                    .put("priorityCode",row.getPriorityCode()).put("safetyFocus",row.getSafetyFocus()).put("evaluationMode",row.getEvaluationMode()).put("title",row.getTitle());
            n.set("schedule",JsonValues.read(row.getScheduleRuleJson()));
            var vs=variants.selectList(new QueryWrapper<TaskGroupVariant>().eq("org_id",b.getOrgId()).eq("task_group_id",row.getId()).orderByAsc("sort_order","id"));
            n.set("variants",JsonValues.tree(vs.stream().map(v->Map.of("id",v.getId(),"variantKey",v.getVariantKey(),"title",v.getTitle(),"content",v.getContent(),"parameters",JsonValues.read(v.getParametersJson()))).toList()));
            result.add(n);
        }
        return List.copyOf(result);
    }
    public List<PlanReviewContextVO.Issue> issues(TaskBundle b,List<JsonNode> groups) {
        var result=new ArrayList<PlanReviewContextVO.Issue>();
        if(groups.isEmpty())result.add(issue("NO_EXECUTION_ITEMS","groups","No selected execution item in source plan"));
        for(var g:groups) {
            String field="groups."+g.path("id").asText();
            if(g.path("schedule").isNull() || g.path("schedule").isMissingNode())result.add(issue("SCHEDULE_REQUIRED",field,"Confirm execution days, slots and windows"));
            if(!g.path("executionConfirmed").asBoolean(false))result.add(issue("ACTION_CONFIRMATION_REQUIRED",field,"Confirm the source action, basic goal and execution detail"));
            if(!templates.current(b.getOrgId(),g.path("template")))result.add(issue("TEMPLATE_UNAVAILABLE",field,"Template missing, changed or disabled"));
            for(var missing:g.path("generationIssues"))result.add(issue(missing.asText(),field,"Required trusted source or professional template is unavailable"));
        }
        return List.copyOf(result);
    }
    public static PlanReviewContextVO.Issue issue(String code,String field,String message){return new PlanReviewContextVO.Issue(code,"BLOCKER",field,message);}
    public String hash(TaskBundle b) {return CanonicalJson.hash(JsonValues.tree(Map.of("sourceReviewHash",b.getSourceReviewHash(),"inputSnapshotId",b.getInputSnapshotId(),"startDate",b.getStartDate().toString(),"endDate",b.getEndDate().toString(),"zoneId",b.getZoneId(),"groups",groups(b),"ruleVersion",TaskTemplateCatalog.RULE_VERSION)));}
    public TaskBundleVO view(TaskBundle b) {
        var gs=groups(b);var input=snapshots.selectById(b.getInputSnapshotId());
        return new TaskBundleVO(b.getId(),b.getPatientId(),b.getPlanVersionId(),b.getSourceReviewSubmissionId(),b.getSourceReviewDecisionId(),String.valueOf(b.getSourceRevision()),b.getVersionNo(),b.getBasedOnBundleId(),String.valueOf(b.getRowVersion()),String.valueOf(b.getContentRevision()),b.getStatus(),b.getStartDate(),b.getEndDate(),b.getZoneId(),b.getGenerationMode(),b.getContentHash(),b.getLatestPreviewId(),b.getInputSnapshotId(),JsonValues.read(input.getPayloadJson()),JsonValues.read(b.getNextCycleReferenceJson()),gs,issues(b,gs));
    }
    public void requireRevision(TaskBundle b,long expected,boolean editable) {
        if(b.getRowVersion()!=expected)throw BusinessException.conflict("Task bundle changed");
        if(editable && !"DRAFT".equals(b.getStatus()))throw BusinessException.conflict("Only task drafts may be edited");
    }
    public void advance(BusinessPrincipal p,TaskBundle b,String status,boolean content,String action) {
        long before=b.getRowVersion();b.setRowVersion(Math.addExact(before,1));b.setUpdatedAt(now());
        if(status!=null)b.setStatus(status);
        if(content){b.setContentRevision(Math.addExact(b.getContentRevision(),1));b.setLatestPreviewId(null);b.setContentHash(hash(b));}
        if(bundles.updateById(b)!=1)throw BusinessException.conflict("Task bundle changed");
        audit(p,b,action,before);
    }
    public void audit(BusinessPrincipal p,TaskBundle b,String action,Long before) {
        var a=stamp(new AuditLog(),p);a.setActorId(p.subjectId());a.setAction(action);a.setTargetType("TASK_BUNDLE");a.setTargetId(b.getId());a.setBeforeRevision(before);a.setAfterRevision(b.getRowVersion());
        a.setRequestId(Optional.ofNullable(MDC.get("requestId")).orElseGet(()->UUID.randomUUID().toString()));a.setChangeSummaryJson(JsonValues.write(Map.of("contentHash",b.getContentHash())));a.setOccurredAt(a.getCreatedAt());audits.insert(a);
    }
}
