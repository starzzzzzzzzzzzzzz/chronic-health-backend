package com.healthsystem.service.impl;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.common.json.*;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.service.TaskBundleService;
import com.healthsystem.vo.TaskBundleVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanDraftValidation.*;

@Service
public class TaskBundleServiceImpl implements TaskBundleService {
    private final TaskBundleStore store;private final TaskSourcePolicy source;private final TaskGenerationService generation;
    public TaskBundleServiceImpl(TaskBundleStore s,TaskSourcePolicy p,TaskGenerationService g){store=s;source=p;generation=g;}
    @Override public TaskBundleVO generate(BusinessPrincipal p,String id,ReviewWorkflowRequest body){return generation.generate(p,id,body);}
    @Override @Transactional(readOnly=true) public TaskBundleVO get(BusinessPrincipal p,String id){return store.view(store.context(p,id,"TASK_READ",false).bundle());}
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public TaskBundleVO update(BusinessPrincipal p,String id,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"expectedRevision","groups");long expected=expected(body);
        var c=store.context(p,id,"TASK_EDIT",true);var b=c.bundle();store.requireRevision(b,expected,true);source.validate(b,c.source());
        if(body.has("groups")) {
            var input=body.path("groups");check(input.isArray() && input.size()<=1000,"Invalid groups");
            var existing=store.groupRows(b);check(input.size()==existing.size(),"Keep all source groups; changing source needs a new approved plan");
            var seen=new HashSet<String>();
            for(var item:input) {
                fields(item,"id","schedule","taskClass","priorityCode","safetyFocus","executionConfirmed","confirmationNote","displayNote");
                String gid=string(item,"id",true,36);check(seen.add(gid),"Duplicate group");
                var g=existing.stream().filter(x->x.getId().equals(gid)).findFirst().orElse(null);check(g!=null,"Foreign task group");
                var data=(ObjectNode)JsonValues.read(g.getContentJson()).deepCopy();
                for(String name:List.of("executionConfirmed","safetyFocus"))if(item.has(name))check(item.path(name).isBoolean(),"Invalid "+name);
                if(item.has("executionConfirmed"))data.put("executionConfirmed",item.path("executionConfirmed").asBoolean());
                if(item.has("confirmationNote"))data.put("confirmationNote",string(item,"confirmationNote",true,2000));
                if(data.path("executionConfirmed").asBoolean())check(!data.path("confirmationNote").asText().isBlank(),"Confirmation note required");
                if(item.has("displayNote"))data.put("displayNote",string(item,"displayNote",false,2000));
                if(item.has("taskClass")){String value=string(item,"taskClass",true,16);check(Set.of("REQUIRED","OPTIONAL").contains(value),"Invalid task class");g.setTaskClass(value);}
                if(item.has("priorityCode")){String value=string(item,"priorityCode",true,8);check(Set.of("L0","L1","L2","L3","L4").contains(value),"Invalid priority");g.setPriorityCode(value);}
                if(item.has("safetyFocus"))g.setSafetyFocus(item.path("safetyFocus").asBoolean());
                check(!g.getSafetyFocus() || ("REQUIRED".equals(g.getTaskClass()) && "L0".equals(g.getPriorityCode())),"Safety tasks must remain required L0");
                if(item.has("schedule")){check(item.path("schedule").isObject(),"Invalid schedule");g.setScheduleRuleJson(JsonValues.write(item.path("schedule")));}
                g.setContentJson(JsonValues.write(data));store.groupRows.updateById(g);
            }
        }
        if(!b.getContentHash().equals(store.hash(b)))store.advance(p,b,null,true,"TASK_BUNDLE_UPDATED");
        return store.view(b);
    }
}
