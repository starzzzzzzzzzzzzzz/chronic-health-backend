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
        int order=0;var shared=new HashMap<String,TaskGroup>();
        for(var i:c.draft().items())if(Boolean.TRUE.equals(i.isSelected()) && Boolean.TRUE.equals(i.executionGoal()) && c.draft().dimensions().stream().anyMatch(d->d.dimensionCode().equals(i.dimensionCode())&&"ENABLED".equals(d.decision()))) {
            var template=templates.find(p.orgId(),p.dataMode(),i.dimensionCode());
            var payload=JsonValues.read(template==null?"{}":template.getContentJson());
            var issues=new ArrayList<String>();
            if(template==null)issues.add("TEMPLATE_UNAVAILABLE");
            if("tcm".equals(i.dimensionCode()) && (template==null || !"MEDICALLY_APPROVED".equals(template.getReviewStatus())))issues.add("MEDICAL_TEMPLATE_REQUIRED");
            JsonNode professional=null;
            if("medication".equals(i.dimensionCode())) {
                for(var entry:JsonValues.read(approved.submission().getContentSnapshotJson()).path("confirmations"))if(("medication.source."+i.id()).equals(entry.path("conditionKey").asText())) {
                    var value=entry.path("confirmation").path("value");professional=value.path("value").isObject()?value.path("value"):value;
                }
                boolean trusted=professional!=null && professional.isObject() && professional.path("schedule").isObject();
                if(trusted)for(String field:List.of("drugName","dose","route","frequency","mealRelation"))trusted&=professional.path(field).isString()&&!professional.path(field).asText().isBlank();
                if(!trusted){professional=null;issues.add("TRUSTED_MEDICATION_SCHEDULE_REQUIRED");}
            }
            var data=new LinkedHashMap<String,Object>();data.put("schemaVersion",TaskTemplateCatalog.RULE_VERSION);data.put("action",i.content());data.put("basicGoal",i.content());data.put("planGoal",c.draft().primaryGoal());
            data.put("sourcePlanItemIds",List.of(i.id()));data.put("sourceFactIds",i.basis().stream().map(PlanComponents.Basis::factId).distinct().sorted().toList());data.put("sourceModuleCodes",i.basis().stream().map(PlanComponents.Basis::moduleCode).distinct().sorted().toList());
            data.put("template",template==null?Map.of():Map.of("id",template.getId(),"version",template.getVersionNo(),"contentHash",template.getContentHash(),"reviewStatus",template.getReviewStatus(),"dataMode",template.getDataMode()));
            data.put("executionConfirmed",false);data.put("confirmationNote",null);data.put("generationIssues",issues);data.put("enhancement","DISABLED");data.put("qualityUnavailableReason","QUALITY_PARAMETERS_NOT_CONFIGURED");
            if(professional!=null)data.put("professionalBinding",professional);
            data.put("stopConditions",payload.path("stopConditions"));data.put("sourceContraindications",i.contraindications());
            data.put("feedbackSchema",Map.of("schemaVersion","completion-v1","fields",List.of("actualExecutedAt","note","reportedSafety"),"required",List.of(),"defaultCompletion","COMPLETED","allFieldsOptional",true));
            var identity=(tools.jackson.databind.node.ObjectNode)JsonValues.tree(i);identity.remove("id");identity.remove("basis");identity.remove("sortOrder");String signature=CanonicalJson.hash(identity);
            var g=Set.of("diet","exercise","routine","other").contains(i.dimensionCode())?shared.get(signature):null;
            if(g!=null) {
                var merged=(tools.jackson.databind.node.ObjectNode)JsonValues.read(g.getContentJson());
                for(String key:List.of("sourcePlanItemIds","sourceFactIds","sourceModuleCodes")) {
                    var ids=new TreeSet<String>();for(var x:merged.path(key))ids.add(x.asText());for(var x:JsonValues.tree(data.get(key)))ids.add(x.asText());merged.set(key,JsonValues.tree(ids));
                }
                g.setContentJson(JsonValues.write(merged));store.groupRows.updateById(g);
            } else {
                g=stamp(new TaskGroup(),p);g.setTaskBundleId(b.getId());g.setDimensionCode(i.dimensionCode());g.setActorType(i.actorType());g.setTitle(i.title());g.setTaskClass("REQUIRED");g.setPriorityCode("L2");g.setSafetyFocus(false);g.setEvaluationMode("COMPLETION_ONLY");g.setSortOrder(order++);g.setContentJson(JsonValues.write(data));
                if(professional!=null)g.setScheduleRuleJson(JsonValues.write(professional.path("schedule")));
                store.groupRows.insert(g);shared.put(signature,g);
                var candidates=payload.path("variants");
                if(Set.of("diet","exercise").contains(i.dimensionCode()) && candidates.isArray() && !candidates.isEmpty()) {
                    var keys=new HashSet<String>();com.healthsystem.service.impl.PlanDraftValidation.check(candidates.size()<=8,"Template pool too large");int position=0;
                    for(var candidate:candidates) {
                        fields(candidate,"variantKey","title","content","parameters");String key=string(candidate,"variantKey",true,64);
                        com.healthsystem.service.impl.PlanDraftValidation.check(keys.add(key)&&candidate.path("parameters").isObject(),"Invalid template variants");
                        var v=stamp(new TaskGroupVariant(),p);v.setTaskGroupId(g.getId());v.setVariantKey(key);v.setTitle(string(candidate,"title",true,200));v.setContent(string(candidate,"content",true,20000));v.setParametersJson(JsonValues.write(candidate.path("parameters")));v.setSortOrder(position++);store.variants.insert(v);
                    }
                } else {
                    var v=stamp(new TaskGroupVariant(),p);v.setTaskGroupId(g.getId());v.setVariantKey("source");v.setTitle(i.title());v.setContent(i.content());v.setParametersJson(professional==null?"{}":JsonValues.write(professional));v.setSortOrder(0);store.variants.insert(v);
                }
            }
            for(var basis:i.basis()) {
                var s=stamp(new TaskGroupSource(),p);s.setTaskGroupId(g.getId());s.setPlanItemId(i.id());s.setFactId(basis.factId());s.setModuleCode(basis.moduleCode());store.sources.insert(s);
            }
        }
        b.setContentHash(store.hash(b));store.bundles.updateById(b);store.audit(p,b,"TASK_BUNDLE_GENERATED",null);return store.view(b);
    }
}
