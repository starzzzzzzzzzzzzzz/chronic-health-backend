package com.healthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.JsonValues;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.entity.*;
import com.healthsystem.exception.BusinessException;
import com.healthsystem.mapper.*;
import com.healthsystem.service.*;
import com.healthsystem.vo.CarePlanDraftVO;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component
public class PlanReviewSupport {
    public record Context(CarePlan plan,CarePlanVersion version,CarePlanDraftVO draft) {}
    private final CarePlanMapper plans;
    private final CarePlanVersionMapper versions;
    private final PatientService patients;
    private final CarePlanService carePlans;
    private final AuditLogMapper audits;
    public PlanReviewSupport(CarePlanMapper plans,CarePlanVersionMapper versions,PatientService patients,CarePlanService carePlans,AuditLogMapper audits) {
        this.plans=plans;this.versions=versions;this.patients=patients;this.carePlans=carePlans;this.audits=audits;
    }
    public Context context(BusinessPrincipal p,String id,String capability,boolean lock) {
        var v=versions.selectOne(new QueryWrapper<CarePlanVersion>().eq("org_id",p.orgId()).eq("id",id));
        if(v==null)throw new BusinessException(404,"Plan version not found");
        var plan=plans.selectOne(new QueryWrapper<CarePlan>().eq("org_id",p.orgId()).eq("id",v.getPlanId()));
        if(plan==null)throw new BusinessException(404,"Plan not found");
        patients.require(p,plan.getPatientId(),capability,lock);
        if(!"PATIENT_READ".equals(capability))patients.require(p,plan.getPatientId(),"PATIENT_READ",false);
        if(lock)v=plans.lockVersion(p.orgId(),id);
        return new Context(plan,v,carePlans.get(p,id));
    }
    public void capability(BusinessPrincipal p,Context c,String ability){patients.require(p,c.plan().getPatientId(),ability,false);}
    public void editable(Context c,long expected) {
        if(c.version().getRowVersion()!=expected)throw BusinessException.conflict("Plan changed; reload before continuing");
        if(!"DRAFT".equals(c.version().getStatus()))throw BusinessException.conflict("Only a draft may be changed");
    }
    public void advance(BusinessPrincipal p,Context c,String status,String action,Map<String,Object> summary) {
        var v=c.version();long before=v.getRowVersion();
        v.setRowVersion(Math.addExact(before,1));v.setUpdatedAt(now());if(status!=null)v.setStatus(status);
        if(versions.updateById(v)!=1)throw BusinessException.conflict("Plan changed");
        audit(p,v,action,before,summary);
    }
    public void audit(BusinessPrincipal p,CarePlanVersion v,String action,Long before,Map<String,Object> summary) {
        var a=stamp(new AuditLog(),p);a.setActorId(p.subjectId());a.setAction(action);a.setTargetType("CARE_PLAN_VERSION");a.setTargetId(v.getId());a.setBeforeRevision(before);a.setAfterRevision(v.getRowVersion());
        a.setRequestId(Optional.ofNullable(MDC.get("requestId")).orElseGet(()->UUID.randomUUID().toString()));a.setChangeSummaryJson(JsonValues.write(summary));a.setOccurredAt(a.getCreatedAt());audits.insert(a);
    }
    public static LocalDateTime now(){return LocalDateTime.now(Clock.systemUTC());}
    public static OffsetDateTime utc(LocalDateTime value){return value==null?null:value.atOffset(ZoneOffset.UTC);}
    public static <T extends ScopedEntity> T stamp(T e,BusinessPrincipal p){e.setId(UUID.randomUUID().toString());e.setOrgId(p.orgId());e.setCreatedBy(p.subjectId());e.setCreatedAt(now());return e;}
}
