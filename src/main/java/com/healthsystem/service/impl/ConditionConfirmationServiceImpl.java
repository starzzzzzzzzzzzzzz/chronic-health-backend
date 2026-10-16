package com.healthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.*;
import com.healthsystem.entity.ConditionConfirmation;
import com.healthsystem.mapper.ConditionConfirmationMapper;
import com.healthsystem.service.ConditionConfirmationService;
import com.healthsystem.vo.ConditionConfirmationVO;
import com.healthsystem.vo.ConditionConfirmationVO.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanDraftValidation.*;
import static com.healthsystem.service.impl.PlanReviewSupport.*;

@Service
public class ConditionConfirmationServiceImpl implements ConditionConfirmationService {
    private final PlanReviewSupport support;
    private final PlanConditionPolicy policy;
    private final ConditionConfirmationMapper confirmations;
    public ConditionConfirmationServiceImpl(PlanReviewSupport support,PlanConditionPolicy policy,ConditionConfirmationMapper confirmations) {
        this.support=support;this.policy=policy;this.confirmations=confirmations;
    }
    @Override @Transactional(readOnly=true)
    public ConditionConfirmationVO get(BusinessPrincipal p,String id){return view(support.context(p,id,"PATIENT_READ",false));}

    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public ConditionConfirmationVO save(BusinessPrincipal p,String id,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"expectedRevision","entries");long expected=expected(body);
        var c=support.context(p,id,"PLAN_CONFIRM",true);support.editable(c,expected);
        var entries=body.get("entries");check(entries!=null && entries.isArray() && !entries.isEmpty() && entries.size()<=1000,"Invalid confirmation entries");
        var requirements=policy.requirements(c.draft()).stream().collect(Collectors.toMap(PlanConditionPolicy.Requirement::key,Function.identity()));
        var old=latest(rows(c));var seen=new HashSet<String>();List<ConditionConfirmation> writes=new ArrayList<>();
        for(var entry:entries) {
            fields(entry,"conditionKey","status","value","sourceFactId","confirmationNote");
            String key=string(entry,"conditionKey",true,100);check(seen.add(key),"Duplicate confirmation key");
            var r=requirements.get(key);check(r!=null,"Unknown condition key");
            String status=string(entry,"status",true,24);check(Set.of("PENDING","CONFIRMED","REJECTED").contains(status),"Invalid confirmation status");
            String note=string(entry,"confirmationNote",!"PENDING".equals(status),1000);
            String source=string(entry,"sourceFactId",false,36);JsonNode value=null;
            if("MEDICATION_SOURCE".equals(r.kind()))support.capability(p,c,"PLAN_CONFIRM_PROFESSIONAL");
            if("CONFIRMED".equals(status)) {
                if("EXECUTION".equals(r.kind())) {
                    check(source==null,"Execution source is recorded in the plan basis");
                    value=entry.get("value");fields(value,"actorType","available","environmentConfirmed");
                    check(r.actorType().equals(string(value,"actorType",true,16)),"Execution actor does not match plan");
                    check(value.path("available").isBoolean() && value.path("available").booleanValue(),"Execution actor must be available");
                    check(value.path("environmentConfirmed").isBoolean() && value.path("environmentConfirmed").booleanValue(),"Execution environment must be verified");
                } else {
                    check(!entry.has("value"),"Professional values must be copied from the source");
                    value=medicationSource(c,r,source);
                }
            } else {
                check(!entry.hasNonNull("value") && source==null,"Unconfirmed conditions cannot assert a value or source");
            }
            var record=stamp(new ConditionConfirmation(),p);record.setPlanVersionId(id);record.setConditionKey(key);record.setRequirementJson(JsonValues.write(r.payload()));
            record.setValueJson(value==null?null:JsonValues.write(value));record.setStatus(status);record.setSourceFactId(source);record.setSubjectRevision(c.version().getContentRevision());record.setBindingHash(r.bindingHash());record.setConfirmationNote(note);
            if("CONFIRMED".equals(status)){record.setConfirmedBy(p.subjectId());record.setConfirmedAt(record.getCreatedAt());}
            var prior=old.get(key);if(prior!=null)record.setSupersedesConfirmationId(prior.getId());
            writes.add(record);
        }
        for(var record:writes)confirmations.insert(record);
        support.advance(p,c,null,"CONDITIONS_RECORDED",Map.of("conditionKeys",new TreeSet<>(seen),"count",writes.size()));
        return view(c);
    }
    private JsonNode medicationSource(Context c,PlanConditionPolicy.Requirement r,String source) {
        check(source!=null,"Professional source required");
        var item=c.draft().items().stream().filter(i->i.id().equals(r.itemId())).findFirst().orElseThrow();
        check(item.basis().stream().anyMatch(b->source.equals(b.factId())),"Medication source must be in item basis");
        JsonNode found=null;
        for(var f:c.draft().inputSnapshot().path("facts"))if(source.equals(f.path("id").asText()))found=f;
        check(found!=null && "medication_order".equals(found.path("factCode").asText()) && "CONFIRMED_PROFESSIONAL".equals(found.path("confirmationStatus").asText()),"Trusted medication source not in input snapshot");
        check(found.path("confirmedBy").isString() && !found.path("confirmedBy").asText().isBlank() && !found.path("confirmedAt").isNull() && !found.path("confirmedAt").isMissingNode(),"Professional source lacks confirmation provenance");
        JsonNode value=found.path("value");check(value.isObject() && !value.isEmpty(),"Professional source value required");return value;
    }
    public ConditionConfirmationVO view(Context c) {
        var all=rows(c);var latest=latest(all);List<ConditionConfirmationVO.Condition> current=new ArrayList<>();
        for(var r:policy.requirements(c.draft())) {
            var row=latest.get(r.key());String status=row==null?"MISSING":row.getStatus();
            if(row!=null && (row.getSubjectRevision()!=c.version().getContentRevision().longValue() || !r.bindingHash().equals(row.getBindingHash())))status="STALE";
            current.add(new ConditionConfirmationVO.Condition(r.key(),r.kind(),r.itemId(),r.actorType(),r.payload(),r.bindingHash(),status,row==null?null:record(row)));
        }
        return new ConditionConfirmationVO(c.version().getId(),String.valueOf(c.version().getRowVersion()),String.valueOf(c.version().getContentRevision()),List.copyOf(current),all.stream().map(this::record).toList());
    }
    public JsonNode manifest(ConditionConfirmationVO view) {
        return JsonValues.tree(view.conditions().stream().map(c->Map.of("conditionKey",c.conditionKey(),"bindingHash",c.bindingHash(),"confirmation",JsonValues.tree(c.record()))).toList());
    }
    private List<ConditionConfirmation> rows(Context c) {
        return confirmations.selectList(new QueryWrapper<ConditionConfirmation>().eq("org_id",c.version().getOrgId()).eq("plan_version_id",c.version().getId()).orderByAsc("created_at","id"));
    }
    private Map<String,ConditionConfirmation> latest(List<ConditionConfirmation> rows) {
        var superseded=rows.stream().map(ConditionConfirmation::getSupersedesConfirmationId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String,ConditionConfirmation> result=new HashMap<>();for(var r:rows)if(!superseded.contains(r.getId()))result.put(r.getConditionKey(),r);return result;
    }
    private Record record(ConditionConfirmation c) {
        return new Record(c.getId(),c.getConditionKey(),JsonValues.read(c.getRequirementJson()),JsonValues.read(c.getValueJson()),c.getStatus(),c.getSourceFactId(),String.valueOf(c.getSubjectRevision()),c.getBindingHash(),c.getConfirmedBy(),utc(c.getConfirmedAt()),c.getConfirmationNote(),c.getSupersedesConfirmationId(),c.getCreatedBy(),utc(c.getCreatedAt()));
    }
}
