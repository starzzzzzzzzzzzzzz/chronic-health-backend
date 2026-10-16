package com.healthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.*;
import com.healthsystem.entity.*;
import com.healthsystem.exception.BusinessException;
import com.healthsystem.mapper.*;
import com.healthsystem.service.*;
import com.healthsystem.vo.CarePlanDraftVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanReviewSupport.*;

@Service
public class PlanVersionServiceImpl implements PlanVersionService {
    private final PlanReviewSupport support;
    private final CarePlanMapper plans;
    private final CarePlanVersionMapper versions;
    private final HealthInputSnapshotMapper snapshots;
    private final PlanDraftStore store;
    private final CarePlanService carePlans;
    public PlanVersionServiceImpl(PlanReviewSupport support,CarePlanMapper plans,CarePlanVersionMapper versions,HealthInputSnapshotMapper snapshots,PlanDraftStore store,CarePlanService carePlans) {
        this.support=support;this.plans=plans;this.versions=versions;this.snapshots=snapshots;this.store=store;this.carePlans=carePlans;
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public CarePlanDraftVO create(BusinessPrincipal p,String planId,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"sourceVersionId","expectedRevision","changeReason");
        String sourceId=string(body,"sourceVersionId",true,36),reason=string(body,"changeReason",true,1000);long expected=expected(body);
        var c=support.context(p,sourceId,"PLAN_EDIT",true);var source=c.version();
        if(!planId.equals(source.getPlanId()))throw new BusinessException(404,"Plan version not found");
        // Re-read after acquiring the patient lock: another request may have advanced the pointer while we waited.
        var plan=plans.selectOne(new QueryWrapper<CarePlan>().eq("org_id",p.orgId()).eq("id",planId));
        if(source.getRowVersion()!=expected || !"APPROVED".equals(source.getStatus()) || !sourceId.equals(plan.getCurrentVersionId()) || !"OPEN".equals(plan.getStatus()))throw BusinessException.conflict("Only the current approved version may create a new draft");
        var snapshot=snapshots.selectOne(new QueryWrapper<HealthInputSnapshot>().eq("org_id",p.orgId()).eq("id",source.getInputSnapshotId()));
        if(snapshot==null)throw new IllegalStateException("Missing source input snapshot");
        var v=stamp(new CarePlanVersion(),p);v.setPlanId(planId);
        var latest=versions.selectOne(new QueryWrapper<CarePlanVersion>().eq("org_id",p.orgId()).eq("plan_id",planId).orderByDesc("version_no").last("LIMIT 1"));
        v.setVersionNo(Math.addExact(latest.getVersionNo(),1));v.setBasedOnVersionId(sourceId);v.setTitle(source.getTitle());v.setStartDate(source.getStartDate());v.setEndDate(source.getEndDate());v.setZoneId(source.getZoneId());
        v.setPrimaryGoal(source.getPrimaryGoal());v.setGoalBasisJson(source.getGoalBasisJson());v.setSupplementalRequirements(source.getSupplementalRequirements());v.setInputSnapshotId(snapshot.getId());v.setChangeReason(reason);
        v.setStatus("DRAFT");v.setRowVersion(0L);v.setContentRevision(0L);v.setUpdatedAt(v.getCreatedAt());
        var content=copyContent(c.draft(),JsonValues.read(snapshot.getPayloadJson()));v.setContentHash(PlanDraftHash.hash(v,content,snapshot));versions.insert(v);store.replace(p,v,content,v.getCreatedAt());
        plan.setCurrentVersionId(v.getId());plan.setRowVersion(Math.addExact(plan.getRowVersion(),1));plan.setUpdatedAt(v.getCreatedAt());plans.updateById(plan);
        support.audit(p,v,"DRAFT_VERSION_CREATED",null,Map.of("sourceVersionId",sourceId,"versionNo",v.getVersionNo(),"contentHash",v.getContentHash()));
        return carePlans.get(p,v.getId());
    }
    private PlanDraftStore.Content copyContent(CarePlanDraftVO d,tools.jackson.databind.JsonNode snapshot) {
        var modules=d.modules().stream().map(m->new PlanComponents.Module(null,m.moduleCode(),m.selectionReason(),m.factIds())).toList();
        var dimensions=d.dimensions().stream().map(v->new PlanComponents.Dimension(null,v.dimensionCode(),v.decision(),v.decisionReason(),v.sortOrder())).toList();
        var focuses=d.managementFocuses().stream().map(f->new PlanComponents.Focus(null,f.diagnosisFactId(),f.primarySubtypeCode(),f.secondarySubtypeCodes(),f.priorityCode(),f.dimensionWeights(),f.contributionBps(),f.adjustmentReason())).toList();
        var items=d.items().stream().map(i->new PlanComponents.Item(null,i.dimensionCode(),i.title(),i.content(),i.executionGoal(),i.actorType(),i.contraindications(),i.professionalParameters(),i.references(),i.isSelected(),i.sortOrder(),i.basis())).toList();
        var patch=JsonValues.tree(Map.of("modules",modules,"dimensions",dimensions,"managementFocuses",focuses,"items",items));
        return PlanDraftValidation.normalize(new PlanDraftStore.Content(List.of(),List.of(),List.of(),List.of()),patch,snapshot);
    }
}
