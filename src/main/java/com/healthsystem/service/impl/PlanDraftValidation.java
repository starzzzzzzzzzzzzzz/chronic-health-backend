package com.healthsystem.service.impl;

import com.healthsystem.common.json.JsonValues;
import com.healthsystem.dto.PlanComponents;
import com.healthsystem.exception.BusinessException;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Validates the complete proposed draft, not only fields present in a PATCH. */
public final class PlanDraftValidation {
 public static final Set<String> DIMENSIONS=Set.of("medication","diet","exercise","routine","monitoring","tcm","other");
 private static final Set<String> MODULES=Set.of("B0","L","R","C","F","A");
 private static final JsonMapper JSON=JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
 private PlanDraftValidation() {}
 public static PlanDraftStore.Content normalize(PlanDraftStore.Content previous,JsonNode patch,JsonNode snapshot) {
  Map<String,JsonNode> facts=new HashMap<>();for(var f:snapshot.path("facts"))facts.put(f.path("id").asText(),f);
  var moduleInputs=read(patch,"modules",previous.modules(),PlanComponents.Module.class,6);
  var dimensionInputs=read(patch,"dimensions",previous.dimensions(),PlanComponents.Dimension.class,7);
  var focusInputs=read(patch,"managementFocuses",previous.managementFocuses(),PlanComponents.Focus.class,100);
  var itemInputs=read(patch,"items",previous.items(),PlanComponents.Item.class,500);
  Map<String,PlanComponents.Module> oldModules=previous.modules().stream().collect(Collectors.toMap(PlanComponents.Module::moduleCode,Function.identity()));
  Map<String,PlanComponents.Dimension> oldDimensions=previous.dimensions().stream().collect(Collectors.toMap(PlanComponents.Dimension::dimensionCode,Function.identity()));
  Map<String,PlanComponents.Focus> oldFocuses=previous.managementFocuses().stream().collect(Collectors.toMap(PlanComponents.Focus::diagnosisFactId,Function.identity()));
  Set<String> oldItemIds=previous.items().stream().map(PlanComponents.Item::id).collect(Collectors.toSet());
  List<PlanComponents.Module> modules=new ArrayList<>();Set<String> codes=new HashSet<>();
  for(var m:moduleInputs){
   check(MODULES.contains(Objects.toString(m.moduleCode(),""))&&codes.add(m.moduleCode()),"Invalid or duplicate module");
   String reason=text(m.selectionReason(),true,20000);
   check(m.factIds()!=null&&!m.factIds().isEmpty()&&m.factIds().size()<=100,"Module facts required");
   var factIds=new TreeSet<String>();for(String id:m.factIds()){check(id!=null&&facts.containsKey(id)&&factIds.add(id),"Invalid or duplicate module fact");}
   var old=oldModules.get(m.moduleCode());modules.add(new PlanComponents.Module(identity(m.id(),old==null?null:old.id()),m.moduleCode(),reason,List.copyOf(factIds)));
  }
  List<PlanComponents.Dimension> dimensions=new ArrayList<>();codes.clear();
  for(var d:dimensionInputs){
   check(DIMENSIONS.contains(Objects.toString(d.dimensionCode(),""))&&codes.add(d.dimensionCode()),"Invalid or duplicate dimension");
   check(Set.of("ENABLED","CLOSED","PENDING").contains(Objects.toString(d.decision(),"")),"Invalid dimension decision");
   String reason=text(d.decisionReason(),!"ENABLED".equals(d.decision()),20000);int order=order(d.sortOrder());var old=oldDimensions.get(d.dimensionCode());
   dimensions.add(new PlanComponents.Dimension(identity(d.id(),old==null?null:old.id()),d.dimensionCode(),d.decision(),reason,order));
  }
  List<PlanComponents.Focus> focuses=new ArrayList<>();codes.clear();boolean contributions=false;int contributionTotal=0;
  for(var f:focusInputs){
   check(f.diagnosisFactId()!=null&&codes.add(f.diagnosisFactId()),"Invalid or duplicate disease focus");
   var fact=facts.get(f.diagnosisFactId());check(fact!=null&&fact.path("factCode").asText().startsWith("diagnosis")&&"CONFIRMED_PROFESSIONAL".equals(fact.path("confirmationStatus").asText()),"Disease focus requires confirmed diagnosis source");
   if(f.dimensionWeights()!=null){check(f.dimensionWeights().keySet().equals(DIMENSIONS),"Weights must cover seven dimensions");long total=0;for(Integer v:f.dimensionWeights().values()){check(v!=null&&v>=0&&v<=10000,"Invalid dimension weight");total+=v;}check(total==10000,"Dimension weights must total 10000");}
   if(f.contributionBps()!=null){contributions=true;check(f.contributionBps()>=0&&f.contributionBps()<=10000,"Invalid contribution");contributionTotal+=f.contributionBps();}
   var old=oldFocuses.get(f.diagnosisFactId());var secondary=f.secondarySubtypeCodes()==null?List.<String>of():f.secondarySubtypeCodes();check(secondary.size()<=30,"Too many subtypes");for(var code:secondary)text(code,true,64);
   focuses.add(new PlanComponents.Focus(identity(f.id(),old==null?null:old.id()),f.diagnosisFactId(),text(f.primarySubtypeCode(),false,64),List.copyOf(secondary),text(f.priorityCode(),false,32),f.dimensionWeights()==null?null:new TreeMap<>(f.dimensionWeights()),f.contributionBps(),text(f.adjustmentReason(),false,20000)));
  }
  if(contributions)check(focuses.stream().allMatch(f->f.contributionBps()!=null)&&contributionTotal==10000,"Contributions must total 10000");
  if(snapshot.has("managementContext"))ManagementFocusPolicy.requireFrozenChoices(focuses,snapshot.path("managementContext"));
  Map<String,PlanComponents.Module> byModule=modules.stream().collect(Collectors.toMap(PlanComponents.Module::moduleCode,Function.identity()));
  Map<String,PlanComponents.Dimension> byDimension=dimensions.stream().collect(Collectors.toMap(PlanComponents.Dimension::dimensionCode,Function.identity()));
  List<PlanComponents.Item> items=new ArrayList<>();Set<String> ids=new HashSet<>();
  for(var i:itemInputs){
   check(i.id()==null||oldItemIds.contains(i.id()),"Foreign item ID");String id=i.id()==null?UUID.randomUUID().toString():i.id();check(ids.add(id),"Duplicate item ID");
   var dimension=byDimension.get(i.dimensionCode());check(dimension!=null,"Item dimension not in draft");boolean selected=i.isSelected()==null||i.isSelected();if(selected)check("ENABLED".equals(dimension.decision()),"Selected item requires enabled dimension");
   String title=text(i.title(),true,200),content=text(i.content(),true,20000);check(Set.of("PATIENT","CAREGIVER","PROFESSIONAL","SHARED").contains(Objects.toString(i.actorType(),"")),"Invalid execution actor");
   check(i.professionalParameters()==null||i.professionalParameters().isNull()||(i.professionalParameters().isObject()&&i.professionalParameters().isEmpty()),"Professional parameter binding is not available in draft-only phase");
   check(i.contraindications()==null||i.contraindications().isNull()||i.contraindications().isObject()||i.contraindications().isArray(),"Invalid contraindications structure");
   if(i.references()!=null&&!i.references().isNull()){check(i.references().isArray()&&i.references().size()<=30,"Invalid references");for(var ref:i.references()){check(ref.isObject(),"Invalid reference");var verification=ref.path("verificationStatus");check(verification.isMissingNode()||verification.isNull()||"UNVERIFIED".equals(verification.asText()),"Reference verification not available in this phase");}}
   var source=i.basis()==null?List.<PlanComponents.Basis>of():i.basis();check(!selected||!source.isEmpty(),"Selected item requires basis");check(source.size()<=100,"Too many basis references");
   var unique=new HashSet<String>();List<PlanComponents.Basis> basis=new ArrayList<>();
   for(var b:source){check(b!=null,"Invalid null basis entry");var module=byModule.get(b.moduleCode());check(module!=null&&facts.containsKey(b.factId())&&module.factIds().contains(b.factId()),"Invalid item fact or module basis");check(unique.add(b.moduleCode()+":"+b.factId()),"Duplicate basis");basis.add(new PlanComponents.Basis(b.moduleCode(),b.factId(),text(b.note(),false,1000)));}
   basis.sort(Comparator.comparing(PlanComponents.Basis::moduleCode).thenComparing(PlanComponents.Basis::factId));
   items.add(new PlanComponents.Item(id,i.dimensionCode(),title,content,Boolean.TRUE.equals(i.executionGoal()),i.actorType(),i.contraindications(),i.professionalParameters(),i.references(),selected,order(i.sortOrder()),List.copyOf(basis)));
  }
  modules.sort(Comparator.comparing(PlanComponents.Module::moduleCode));focuses.sort(Comparator.comparing(PlanComponents.Focus::diagnosisFactId));dimensions.sort(Comparator.comparing(PlanComponents.Dimension::dimensionCode));items.sort(Comparator.comparing(PlanComponents.Item::sortOrder).thenComparing(PlanComponents.Item::id));
  return new PlanDraftStore.Content(List.copyOf(modules),List.copyOf(focuses),List.copyOf(dimensions),List.copyOf(items));
 }
 private static <T> List<T> read(JsonNode patch,String key,List<T> old,Class<T> type,int max){
  if(!patch.has(key))return old;var n=patch.get(key);check(n.isArray()&&n.size()<=max,"Invalid "+key+" collection");List<T> out=new ArrayList<>();
  try{for(var element:n){check(element.isObject(),"Invalid "+key+" item");out.add(JSON.treeToValue(element,type));}}catch(BusinessException e){throw e;}catch(Exception e){throw BusinessException.badRequest("Invalid "+key+" fields");}
  return out;
 }
 public static String text(String s,boolean required,int max){check((!required||s!=null&&!s.isBlank())&&(s==null||s.length()<=max),"Invalid text field");return s;}
 public static void period(LocalDate start,LocalDate end,String zone){check(start!=null&&end!=null&&!start.isAfter(end),"Invalid period");check(zone!=null&&ZoneId.getAvailableZoneIds().contains(zone),"Invalid time zone");}
 public static long revision(String s){try{check(s!=null&&s.matches("0|[1-9][0-9]{0,18}"),"Invalid revision");return Long.parseLong(s);}catch(NumberFormatException e){throw BusinessException.badRequest("Invalid revision");}}
 public static void check(boolean ok,String message){if(!ok)throw BusinessException.badRequest(message);}
 private static int order(Integer n){int v=n==null?0:n;check(v>=0&&v<=10000,"Invalid display order");return v;}
 private static String identity(String supplied,String previous){check(supplied==null||supplied.equals(previous),"Foreign component ID");return previous==null?UUID.randomUUID().toString():previous;}
}
