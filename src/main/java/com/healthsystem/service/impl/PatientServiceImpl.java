package com.healthsystem.service.impl;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.common.json.JsonValues;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import com.healthsystem.service.PatientService;
import com.healthsystem.vo.*;
import com.healthsystem.vo.PatientHealthSummaryVO.*;
import com.healthsystem.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class PatientServiceImpl implements PatientService {
    private final PatientMapper patients;private final PatientAccessGrantMapper grants;private final HealthFactMapper facts;
    public PatientServiceImpl(PatientMapper patients,PatientAccessGrantMapper grants,HealthFactMapper facts) {this.patients=patients;this.grants=grants;this.facts=facts;}
    @Override @Transactional(readOnly=true)
    public PageVO<PatientSummaryVO> list(BusinessPrincipal principal,int page,int size,String query) {
        if(page<1||size<1||size>100||(query!=null&&query.length()>100)) throw BusinessException.badRequest("Invalid pagination or query");
        String escaped=query==null?null:query.replace("=","==").replace("%","=%").replace("_","=_");
        LocalDateTime now=LocalDateTime.now(Clock.systemUTC());
        long count=patients.countAccessible(principal.orgId(),principal.subjectId(),principal.dataMode(),escaped,now);
        var items=patients.listAccessible(principal.orgId(),principal.subjectId(),principal.dataMode(),escaped,now,((long)page-1)*size,size).stream().map(PatientServiceImpl::summary).toList();
        return new PageVO<>(page,size,count,items);
    }
    @Override public Patient require(BusinessPrincipal principal,String id,String capability,boolean lock) {
        Patient patient=patients.findScoped(id,principal.orgId(),principal.dataMode(),lock);
        if(patient==null) throw new BusinessException(404,"Patient not found");
        LocalDateTime now=LocalDateTime.now(Clock.systemUTC());
        var condition=new QueryWrapper<PatientAccessGrant>().eq("org_id",principal.orgId()).eq("patient_id",id).eq("subject_id",principal.subjectId()).eq("capability",capability).isNull("revoked_at").le("valid_from",now).and(q->q.isNull("valid_until").or().gt("valid_until",now));
        if(grants.selectCount(condition)==0) throw new BusinessException(403,"Patient access denied");
        return patient;
    }
    @Override @Transactional(readOnly=true)
    public PatientHealthSummaryVO healthSummary(BusinessPrincipal principal,String id) {
        Patient patient=require(principal,id,"PATIENT_READ",false);
        var current=facts.currentFacts(principal.orgId(),id);
        Map<String,AxisVO> axes=new LinkedHashMap<>();List<String> missing=new ArrayList<>();
        for(String axis:List.of("H","F","B","C","E","G")) {
            var entries=current.stream().filter(f->axis.equals(f.getAxisCode())).toList();
            boolean available=entries.stream().anyMatch(f->!"UNKNOWN".equals(f.getConfirmationStatus())&&f.getValueJson()!=null);
            axes.put(axis,new AxisVO(available?"AVAILABLE":"UNKNOWN",entries.stream().map(HealthFact::getId).toList()));
            if(!available)missing.add(axis);
        }
        Map<String,List<String>> grouped=new TreeMap<>();
        for(var f:current)if(f.getConflictGroupKey()!=null)grouped.computeIfAbsent(f.getConflictGroupKey(),k->new ArrayList<>()).add(f.getId());
        var conflicts=grouped.entrySet().stream().filter(e->e.getValue().size()>1).map(e->new ConflictVO(e.getKey(),e.getValue())).toList();
        var data=current.stream().map(f->new FactVO(f.getId(),f.getAxisCode(),f.getFactCode(),JsonValues.read(f.getValueJson()),f.getUnit(),f.getSourceType(),JsonValues.read(f.getSourceLocatorJson()),offset(f.getObservedAt()),f.getConfidence(),f.getConfirmationStatus(),f.getConfirmedBy(),offset(f.getConfirmedAt()))).toList();
        return new PatientHealthSummaryVO(summary(patient),String.valueOf(patient.getProfileRevision()),axes,data,conflicts,missing);
    }
    public static PatientSummaryVO summary(Patient p) { return new PatientSummaryVO(p.getId(),p.getName(),p.getSex(),p.getBirthDate(),p.getDataMode(),String.valueOf(p.getProfileRevision())); }
    private static OffsetDateTime offset(LocalDateTime t) {return t==null?null:t.atOffset(ZoneOffset.UTC);}
}
