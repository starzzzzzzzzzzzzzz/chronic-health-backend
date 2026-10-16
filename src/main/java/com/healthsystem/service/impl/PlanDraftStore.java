package com.healthsystem.service.impl;
import com.healthsystem.common.json.JsonValues;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.PlanComponents;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

/** Persistence of the child rows, always called inside the parent aggregate transaction. */
@Component
public class PlanDraftStore {
 private final CarePlanModuleMapper modules;private final PlanManagementFocusMapper focuses;private final CarePlanDimensionMapper dimensions;private final CarePlanItemMapper items;private final PlanItemBasisMapper bases;
 public record Content(List<PlanComponents.Module> modules,List<PlanComponents.Focus> managementFocuses,List<PlanComponents.Dimension> dimensions,List<PlanComponents.Item> items) {}
 public PlanDraftStore(CarePlanModuleMapper modules,PlanManagementFocusMapper focuses,CarePlanDimensionMapper dimensions,CarePlanItemMapper items,PlanItemBasisMapper bases){this.modules=modules;this.focuses=focuses;this.dimensions=dimensions;this.items=items;this.bases=bases;}
 public Content load(CarePlanVersion v) {
  var ms=modules.selectList(scope(v));var ds=dimensions.selectList(scope(v));var fs=focuses.selectList(scope(v));var is=items.selectList(scope(v));
  Map<String,String> moduleCodes=new HashMap<>(),dimensionCodes=new HashMap<>();ms.forEach(m->moduleCodes.put(m.getId(),m.getModuleCode()));ds.forEach(d->dimensionCodes.put(d.getId(),d.getDimensionCode()));
  var mvo=ms.stream().map(m->new PlanComponents.Module(m.getId(),m.getModuleCode(),m.getSelectionReason(),strings(JsonValues.read(m.getBasisJson()).path("factIds")))).sorted(Comparator.comparing(PlanComponents.Module::moduleCode)).toList();
  var dvo=ds.stream().map(d->new PlanComponents.Dimension(d.getId(),d.getDimensionCode(),d.getDecision(),d.getDecisionReason(),d.getSortOrder())).sorted(Comparator.comparing(PlanComponents.Dimension::dimensionCode)).toList();
  var fvo=fs.stream().map(f->{Map<String,Integer> weights=null;if(f.getDimensionWeightsJson()!=null){weights=new TreeMap<>();for(var e:JsonValues.read(f.getDimensionWeightsJson()).properties())weights.put(e.getKey(),e.getValue().intValue());}return new PlanComponents.Focus(f.getId(),f.getDiagnosisFactId(),f.getPrimarySubtypeCode(),strings(JsonValues.read(f.getSecondarySubtypesJson())),f.getPriorityCode(),weights,f.getContributionBps(),f.getAdjustmentReason());}).sorted(Comparator.comparing(PlanComponents.Focus::diagnosisFactId)).toList();
  List<PlanComponents.Item> ivo=new ArrayList<>();
  for(var i:is){
   var bs=bases.selectList(new QueryWrapper<PlanItemBasis>().eq("org_id",v.getOrgId()).eq("plan_item_id",i.getId()));
   var bvo=bs.stream().map(b->new PlanComponents.Basis(moduleCodes.get(b.getModuleId()),b.getFactId(),b.getBasisNote())).sorted(Comparator.comparing(PlanComponents.Basis::moduleCode).thenComparing(PlanComponents.Basis::factId)).toList();
   ivo.add(new PlanComponents.Item(i.getId(),dimensionCodes.get(i.getDimensionId()),i.getTitle(),i.getContent(),i.getExecutionGoal(),i.getActorType(),JsonValues.read(i.getContraindicationsJson()),JsonValues.read(i.getProfessionalParametersJson()),JsonValues.read(i.getReferencesJson()),i.getIsSelected(),i.getSortOrder(),bvo));
  }
  ivo.sort(Comparator.comparing(PlanComponents.Item::sortOrder).thenComparing(PlanComponents.Item::id));return new Content(mvo,fvo,dvo,List.copyOf(ivo));
 }
 public void replace(BusinessPrincipal p,CarePlanVersion v,Content c,LocalDateTime now){
  Map<String,ScopedEntity> old=new HashMap<>();
  var oldItems=items.selectList(scope(v));var oldModules=modules.selectList(scope(v));var oldDimensions=dimensions.selectList(scope(v));var oldFocuses=focuses.selectList(scope(v));
  oldItems.forEach(e->old.put(e.getId(),e));oldModules.forEach(e->old.put(e.getId(),e));oldDimensions.forEach(e->old.put(e.getId(),e));oldFocuses.forEach(e->old.put(e.getId(),e));
  for(var item:oldItems)bases.delete(new QueryWrapper<PlanItemBasis>().eq("org_id",p.orgId()).eq("plan_item_id",item.getId()));
  items.delete(scope(v));modules.delete(scope(v));dimensions.delete(scope(v));focuses.delete(scope(v));
  Map<String,String> mid=new HashMap<>(),did=new HashMap<>();
  for(var m:c.modules()){
   var e=stamp(new CarePlanModule(),m.id(),p,now,old);e.setPlanVersionId(v.getId());e.setModuleCode(m.moduleCode());e.setSelectionReason(m.selectionReason());e.setBasisJson(JsonValues.write(Map.of("factIds",m.factIds())));modules.insert(e);mid.put(m.moduleCode(),e.getId());
  }
  for(var d:c.dimensions()){
   var e=stamp(new CarePlanDimension(),d.id(),p,now,old);e.setPlanVersionId(v.getId());e.setDimensionCode(d.dimensionCode());e.setDecision(d.decision());e.setDecisionReason(d.decisionReason());e.setSortOrder(d.sortOrder());dimensions.insert(e);did.put(d.dimensionCode(),e.getId());
  }
  for(var f:c.managementFocuses()){
   var e=stamp(new PlanManagementFocus(),f.id(),p,now,old);e.setPlanVersionId(v.getId());e.setDiagnosisFactId(f.diagnosisFactId());e.setPrimarySubtypeCode(f.primarySubtypeCode());e.setSecondarySubtypesJson(JsonValues.write(f.secondarySubtypeCodes()));e.setPriorityCode(f.priorityCode());e.setDimensionWeightsJson(f.dimensionWeights()==null?null:JsonValues.write(f.dimensionWeights()));e.setContributionBps(f.contributionBps());e.setAdjustmentReason(f.adjustmentReason());focuses.insert(e);
  }
  for(var i:c.items()){
   var e=stamp(new CarePlanItem(),i.id(),p,now,old);e.setPlanVersionId(v.getId());e.setDimensionId(did.get(i.dimensionCode()));e.setTitle(i.title());e.setContent(i.content());e.setExecutionGoal(i.executionGoal());e.setActorType(i.actorType());e.setContraindicationsJson(nullableJson(i.contraindications()));e.setProfessionalParametersJson(nullableJson(i.professionalParameters()));e.setReferencesJson(nullableJson(i.references()));e.setIsSelected(i.isSelected());e.setSortOrder(i.sortOrder());items.insert(e);
   for(var b:i.basis()) {var basis=stamp(new PlanItemBasis(),UUID.randomUUID().toString(),p,now,Map.of());basis.setPlanItemId(e.getId());basis.setModuleId(mid.get(b.moduleCode()));basis.setFactId(b.factId());basis.setBasisNote(b.note());bases.insert(basis);}
  }
 }
 private static <T> QueryWrapper<T> scope(CarePlanVersion v){return new QueryWrapper<T>().eq("org_id",v.getOrgId()).eq("plan_version_id",v.getId());}
 private static <T extends ScopedEntity> T stamp(T entity,String id,BusinessPrincipal p,LocalDateTime now,Map<String,ScopedEntity> old){entity.setId(id);entity.setOrgId(p.orgId());var prior=old.get(id);entity.setCreatedAt(prior==null?now:prior.getCreatedAt());entity.setCreatedBy(prior==null?p.subjectId():prior.getCreatedBy());return entity;}
 private static List<String> strings(tools.jackson.databind.JsonNode values){if(!values.isArray())return List.of();List<String> out=new ArrayList<>();for(var v:values)out.add(v.asText());return List.copyOf(out);}
 private static String nullableJson(tools.jackson.databind.JsonNode n){return n==null||n.isNull()?null:JsonValues.write(n);}
}
