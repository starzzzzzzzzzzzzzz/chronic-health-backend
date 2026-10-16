package com.healthsystem.service.impl;
import com.healthsystem.vo.*;
import com.healthsystem.vo.PlanReviewContextVO.Issue;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PlanReviewValidation {
    public List<Issue> issues(CarePlanDraftVO plan,ConditionConfirmationVO conditions) {
        List<Issue> issues=new ArrayList<>();
        if(plan.primaryGoal()==null || plan.primaryGoal().isBlank())add(issues,"PRIMARY_GOAL_REQUIRED","primaryGoal","A primary goal is required");
        var basis=plan.goalBasis().path("factIds");
        Set<String> facts=new HashSet<>();for(var f:plan.inputSnapshot().path("facts"))facts.add(f.path("id").asText());
        boolean valid=basis.isArray() && !basis.isEmpty();
        for(var id:basis)if(!id.isString() || !facts.contains(id.asText()))valid=false;
        if(!valid)add(issues,"GOAL_BASIS_REQUIRED","goalBasis.factIds","Goal requires facts from its input snapshot");
        if(plan.modules().isEmpty())add(issues,"MODULE_REQUIRED","modules","An evidence-backed module is required");
        var selected=plan.items().stream().filter(i->Boolean.TRUE.equals(i.isSelected())).toList();
        if(selected.isEmpty())add(issues,"SELECTED_ITEM_REQUIRED","items","At least one selected recommendation is required");
        for(var d:plan.dimensions()) {
            if("PENDING".equals(d.decision()))add(issues,"DIMENSION_PENDING","dimensions."+d.dimensionCode(),"Resolve pending dimensions before review");
            if("ENABLED".equals(d.decision()) && selected.stream().noneMatch(i->d.dimensionCode().equals(i.dimensionCode())))add(issues,"EMPTY_ENABLED_DIMENSION","dimensions."+d.dimensionCode(),"Enabled dimension has no selected item");
        }
        for(var c:conditions.conditions())if(!"CONFIRMED".equals(c.status()))add(issues,"CONDITION_NOT_CONFIRMED","conditions."+c.conditionKey(),"Required condition is missing, stale or unconfirmed");
        for(var i:selected)if("tcm".equals(i.dimensionCode()) && Boolean.TRUE.equals(i.executionGoal()))add(issues,"TCM_TEMPLATE_REVIEW_REQUIRED","items."+i.id(),"Reviewed platform TCM templates are not integrated yet");
        return List.copyOf(issues);
    }
    private void add(List<Issue> issues,String code,String field,String message){issues.add(new Issue(code,"BLOCKER",field,message));}
}
