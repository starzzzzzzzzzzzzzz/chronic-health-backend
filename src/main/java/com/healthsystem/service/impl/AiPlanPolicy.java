package com.healthsystem.service.impl;

import com.healthsystem.common.json.JsonValues;
import com.healthsystem.exception.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.healthsystem.service.impl.PlanDraftValidation.*;

/** Structural/source checks supplement, and never replace, clinical review. */
@Component
public class AiPlanPolicy {
    public static final String VERSION="ai-plan-rules-v4";
    private static final Set<String> ROOT=Set.of("schemaVersion","primaryGoal","goalFactIds","modules","managementFocuses","dimensions","items","missingInformation","warnings");
    public ObjectNode input(JsonNode snapshot,String requirements) {
        var out=(ObjectNode)JsonValues.tree(Map.of("schemaVersion","ai-plan-input-v1","dataMode","DEMO"));
        var facts=out.putArray("facts");
        for(var fact:snapshot.path("facts")) {
            var projected=facts.addObject();
            for(String field:List.of("id","axisCode","factCode","value","unit","observedAt","sourceType","confirmationStatus"))
                if(fact.has(field))projected.set(field,fact.get(field));
        }
        out.set("missingFields",snapshot.path("missingFields"));
        out.set("conflicts",snapshot.path("conflicts"));
        out.put("supplementalRequirements",Objects.toString(requirements,""));
        if(snapshot.has("managementContext"))out.set("managementContext",ManagementFocusPolicy.modelContext(snapshot.path("managementContext")));
        return out;
    }
    public ObjectNode validate(String raw,JsonNode snapshot) {
        check(raw!=null&&raw.length()<=100_000,"AI output too large or empty");
        JsonNode n;
        try { n=JsonValues.read(raw); } catch(Exception e) { throw BusinessException.badRequest("AI output is not valid JSON"); }
        check(n.isObject()&&n.propertyNames().equals(ROOT),"Invalid AI candidate fields");
        check("ai-plan-v1".equals(n.path("schemaVersion").asText()),"Invalid candidate schema version");
        check(n.path("primaryGoal").isString(),"Invalid primary goal");text(n.path("primaryGoal").asText(),true,1000);
        for(String field:List.of("missingInformation","warnings")) {
            var values=n.path(field);check(values.isArray()&&values.size()<=20,"Invalid "+field);
            for(var value:values){check(value.isString(),"Invalid explanation");text(value.asText(),true,1000);}
        }
        var factIds=new HashSet<String>();for(var f:snapshot.path("facts"))factIds.add(f.path("id").asText());
        var goal=n.path("goalFactIds");check(goal.isArray()&&!goal.isEmpty()&&goal.size()<=30,"Goal facts required");
        var used=new HashSet<String>();
        for(var id:goal)check(id.isString()&&factIds.contains(id.asText())&&used.add(id.asText()),"Goal fact not in input snapshot");
        check(n.path("dimensions").isArray()&&n.path("dimensions").size()==7,"Seven dimension decisions required");
        check(n.path("items").isArray()&&n.path("items").size()<=20,"Too many AI plan items");
        for(var dimension:n.path("dimensions"))
            if(Set.of("medication","tcm").contains(dimension.path("dimensionCode").asText()))
                check(!"ENABLED".equals(dimension.path("decision").asText()),"Professional source binding requires manual confirmation");
        for(var item:n.path("items")) {
            check(!Set.of("medication","tcm").contains(item.path("dimensionCode").asText()),"Professional source binding requires manual confirmation");
            check(!item.hasNonNull("id"),"AI must not assign item IDs");
            check(!item.hasNonNull("references")||item.path("references").isArray()&&item.path("references").isEmpty(),"AI cannot invent reference sources");
            check(!item.hasNonNull("professionalParameters")||item.path("professionalParameters").isObject()&&item.path("professionalParameters").isEmpty(),"AI cannot assign professional parameters");
        }
        for(var module:n.path("modules"))if("C".equals(module.path("moduleCode").asText())) {
            var ids=new HashSet<String>();for(var id:module.path("factIds"))ids.add(id.asText());
            boolean confirmed=false;
            for(var fact:snapshot.path("facts"))if(ids.contains(fact.path("id").asText())
                &&fact.path("factCode").asText().startsWith("diagnosis")
                &&"CONFIRMED_PROFESSIONAL".equals(fact.path("confirmationStatus").asText()))confirmed=true;
            check(confirmed,"C module requires a confirmed diagnosis fact");
        }
        var patch=(ObjectNode)JsonValues.tree(Map.of("primaryGoal",n.path("primaryGoal").asText(),"goalBasis",Map.of("factIds",goal,"ruleVersion",VERSION)));
        for(String field:List.of("modules","managementFocuses","dimensions","items"))patch.set(field,n.path(field).deepCopy());
        if(snapshot.has("managementContext")) {
            // Provider values are never allowed to replace operator-confirmed selections or arithmetic.
            check(n.path("managementFocuses").isArray(),"Invalid managementFocuses");
            for(var focus:n.path("managementFocuses")) {
                boolean known=false;
                for(var fact:snapshot.path("facts"))if(focus.path("diagnosisFactId").asText().equals(fact.path("id").asText())
                        &&fact.path("factCode").asText().startsWith("diagnosis")&&"CONFIRMED_PROFESSIONAL".equals(fact.path("confirmationStatus").asText()))known=true;
                check(known,"Disease focus requires confirmed diagnosis source");
            }
            var management=snapshot.path("managementContext");
            patch.set("managementFocuses",management.path("focuses").deepCopy());
            ((ObjectNode)patch.path("goalBasis")).set("managementContext",management.deepCopy());
        }
        completeModuleFactIndex(patch,factIds);
        for(var dimension:patch.path("dimensions"))if("ENABLED".equals(dimension.path("decision").asText())) {
            boolean selected=false;
            for(var item:patch.path("items"))if(dimension.path("dimensionCode").asText().equals(item.path("dimensionCode").asText())
                &&(!item.hasNonNull("isSelected")||item.path("isSelected").asBoolean()))selected=true;
            check(selected,"Enabled dimension requires a selected item");
        }
        // Shared draft checks enforce six module codes, confirmed diagnoses, weights and basis links.
        normalize(new PlanDraftStore.Content(List.of(),List.of(),List.of(),List.of()),patch,snapshot);
        if(snapshot.has("managementContext"))((ObjectNode)patch.path("goalBasis")).set("coordinationReport",
                JsonValues.tree(ManagementCoordinationPolicy.report(patch.path("items"),snapshot.path("managementContext"))));
        return patch;
    }
    /** Module factIds is a redundant index of evidence already explicitly assigned in item basis. */
    private static void completeModuleFactIndex(ObjectNode patch,Set<String> facts) {
        var modules=new HashMap<String,JsonNode>();
        check(patch.path("modules").isArray(),"Invalid modules collection");
        for(var module:patch.path("modules")) {
            check(module.isObject()&&module.path("factIds").isArray(),"Invalid module facts");
            check(modules.put(module.path("moduleCode").asText(),module)==null,"Duplicate module");
        }
        for(var item:patch.path("items"))for(var basis:item.path("basis")) {
            String fact=basis.path("factId").asText();
            var module=modules.get(basis.path("moduleCode").asText());
            check(module!=null&&basis.path("factId").isString()&&facts.contains(fact),"Invalid item fact or module basis");
            var ids=(tools.jackson.databind.node.ArrayNode)module.path("factIds");
            boolean present=false;for(var id:ids)if(fact.equals(id.asText()))present=true;
            if(!present)ids.add(fact);
        }
    }
    public List<Map<String,String>> normalizations(JsonNode raw,JsonNode candidate) {
        var changes=new ArrayList<Map<String,String>>();
        if(!raw.path("managementFocuses").equals(candidate.path("managementFocuses")))
            changes.add(Map.of("code","CONFIRMED_MANAGEMENT_PRESERVED","note","侧重与权重取自冻结的人工确认，不采用模型重写值"));
        for(var module:candidate.path("modules")) {
            var original=new HashSet<String>();
            for(var source:raw.path("modules"))if(source.path("moduleCode").asText().equals(module.path("moduleCode").asText()))
                for(var id:source.path("factIds"))original.add(id.asText());
            for(var id:module.path("factIds"))if(!original.contains(id.asText()))
                changes.add(Map.of("code","MODULE_FACT_INDEX_COMPLETED","moduleCode",module.path("moduleCode").asText(),"factId",id.asText()));
        }
        return List.copyOf(changes);
    }
}
