package com.healthsystem.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.*;
import com.healthsystem.entity.*;
import com.healthsystem.service.PatientService;
import com.healthsystem.vo.TaskBundleVO;
import com.healthsystem.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanReviewSupport.*;

@Service
public class TaskGenerationService {
    private final PlanReviewSupport plans;private final TaskSourcePolicy source;private final TaskBundleStore store;
    private final PatientService patients;private final TaskTemplateCatalog templates;
    public TaskGenerationService(PlanReviewSupport p,TaskSourcePolicy s,TaskBundleStore st,PatientService ps,TaskTemplateCatalog t){plans=p;source=s;store=st;patients=ps;templates=t;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public TaskBundleVO generate(BusinessPrincipal p,String planId,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"expectedRevision");long expected=expected(body);
        var c=plans.context(p,planId,"TASK_EDIT",true);
        if(!"DEMO".equals(p.dataMode()))throw new BusinessException(403,"Rule scaffolds are restricted to DEMO");
        if(c.version().getRowVersion()!=expected)throw BusinessException.conflict("Source plan changed");
        var approved=source.approved(c);var latest=patients.healthSummary(p,c.plan().getPatientId());
        var snap=stamp(new HealthInputSnapshot(),p);snap.setPatientId(c.plan().getPatientId());snap.setStage("TASK");snap.setProfileRevision(Long.valueOf(latest.profileRevision()));
        snap.setCapturedAt(snap.getCreatedAt());snap.setSchemaVersion("patient-input-v1");snap.setPayloadJson(JsonValues.write(latest));snap.setPayloadHash(CanonicalJson.hash(JsonValues.tree(latest)));store.snapshots.insert(snap);
        var previousIds=new HashSet<String>();for(var f:c.draft().inputSnapshot().path("facts"))previousIds.add(f.path("id").asText());
        var changed=latest.facts().stream().filter(f->!previousIds.contains(f.id())).map(f->f.id()).sorted().toList();
        var b=stamp(new TaskBundle(),p);b.setPatientId(c.plan().getPatientId());b.setPlanVersionId(planId);b.setSourceReviewSubmissionId(approved.submission().getId());b.setSourceReviewDecisionId(approved.decision().getId());
        b.setSourceRevision(c.version().getContentRevision());b.setSourcePlanHash(c.draft().contentHash());b.setSourceReviewHash(approved.submission().getContentHash());b.setSourceSnapshotJson(approved.submission().getContentSnapshotJson());
        b.setInputSnapshotId(snap.getId());b.setNextCycleReferenceJson(JsonValues.write(Map.of("changedFactIds",changed,"sourceProfileRevision",c.draft().inputSnapshot().path("profileRevision").asText(),"executionProfileRevision",latest.profileRevision())));
        int next=store.bundles.selectList(new QueryWrapper<TaskBundle>().eq("org_id",p.orgId()).eq("plan_version_id",planId)).stream().mapToInt(TaskBundle::getVersionNo).max().orElse(0)+1;
        b.setVersionNo(next);b.setRowVersion(0L);b.setContentRevision(0L);b.setStatus("DRAFT");b.setStartDate(c.draft().startDate());b.setEndDate(c.draft().endDate());b.setZoneId(c.draft().zoneId());b.setUpdatedAt(now());b.setGenerationMode("RULE_BASED_DEMO");b.setContentHash("0".repeat(64));store.bundles.insert(b);
        int order=0;
        for(var i:c.draft().items())if(Boolean.TRUE.equals(i.isSelected()) && Boolean.TRUE.equals(i.executionGoal()) && c.draft().dimensions().stream().anyMatch(d->d.dimensionCode().equals(i.dimensionCode())&&"ENABLED".equals(d.decision()))) {
            var template=templates.find(p.orgId(),p.dataMode(),i.dimensionCode());
            var payload=JsonValues.read(template==null?"{}":template.getContentJson());
            var issues=new ArrayList<String>();
            if(template==null)issues.add("TEMPLATE_UNAVAILABLE");
            if("tcm".equals(i.dimensionCode()) && (template==null || !"MEDICALLY_APPROVED".equals(template.getReviewStatus())))issues.add("MEDICAL_TEMPLATE_REQUIRED");
            if("medication".equals(i.dimensionCode()))issues.add("TRUSTED_MEDICATION_SCHEDULE_REQUIRED");
            var data=new LinkedHashMap<String,Object>();data.put("schemaVersion",TaskTemplateCatalog.RULE_VERSION);data.put("action",i.content());data.put("basicGoal",i.content());data.put("planGoal",c.draft().primaryGoal());
            data.put("sourcePlanItemIds",List.of(i.id()));data.put("sourceFactIds",i.basis().stream().map(PlanComponents.Basis::factId).distinct().sorted().toList());data.put("sourceModuleCodes",i.basis().stream().map(PlanComponents.Basis::moduleCode).distinct().sorted().toList());
            data.put("template",template==null?Map.of():Map.of("id",template.getId(),"version",template.getVersionNo(),"contentHash",template.getContentHash(),"reviewStatus",template.getReviewStatus(),"dataMode",template.getDataMode()));
            data.put("executionConfirmed",false);data.put("confirmationNote",null);data.put("generationIssues",issues);data.put("enhancement","DISABLED");data.put("qualityUnavailableReason","QUALITY_PARAMETERS_NOT_CONFIGURED");
            data.put("stopConditions",payload.path("stopConditions"));data.put("sourceContraindications",i.contraindications());
            data.put("feedbackSchema",Map.of("schemaVersion","completion-v1","fields",List.of("actualExecutedAt","note","reportedSafety"),"required",List.of(),"defaultCompletion","COMPLETED","allFieldsOptional",true));
            var g=stamp(new TaskGroup(),p);g.setTaskBundleId(b.getId());g.setDimensionCode(i.dimensionCode());g.setActorType(i.actorType());g.setTitle(i.title());g.setTaskClass("REQUIRED");g.setPriorityCode("L2");g.setSafetyFocus(false);g.setEvaluationMode("COMPLETION_ONLY");g.setSortOrder(order++);g.setContentJson(JsonValues.write(data));store.groupRows.insert(g);
            var v=stamp(new TaskGroupVariant(),p);v.setTaskGroupId(g.getId());v.setVariantKey("source");v.setTitle(i.title());v.setContent(i.content());v.setParametersJson("{}");v.setSortOrder(0);store.variants.insert(v);
            for(var basis:i.basis()) {
                var s=stamp(new TaskGroupSource(),p);s.setTaskGroupId(g.getId());s.setPlanItemId(i.id());s.setFactId(basis.factId());s.setModuleCode(basis.moduleCode());store.sources.insert(s);
            }
        }
        b.setContentHash(store.hash(b));store.bundles.updateById(b);store.audit(p,b,"TASK_BUNDLE_GENERATED",null);return store.view(b);
    }
}
