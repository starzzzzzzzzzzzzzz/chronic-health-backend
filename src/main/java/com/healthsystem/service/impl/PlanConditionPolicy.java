package com.healthsystem.service.impl;

import com.healthsystem.common.json.*;
import com.healthsystem.dto.PlanComponents.Item;
import com.healthsystem.vo.CarePlanDraftVO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.util.*;

/** Local v1 execution attestation, not a medical rule editor or task scheduler. */
@Component
public class PlanConditionPolicy {
    public record Requirement(String key,String kind,String itemId,String actorType,JsonNode payload,String bindingHash) {}
    public List<Requirement> requirements(CarePlanDraftVO draft) {
        List<Requirement> result=new ArrayList<>();
        for(Item item:draft.items())if(Boolean.TRUE.equals(item.isSelected()) && Boolean.TRUE.equals(item.executionGoal())) {
            result.add(requirement(draft,item,"execution."+item.id(),"EXECUTION"));
            if("medication".equals(item.dimensionCode()))result.add(requirement(draft,item,"medication.source."+item.id(),"MEDICATION_SOURCE"));
        }
        result.sort(Comparator.comparing(Requirement::key));return List.copyOf(result);
    }
    private Requirement requirement(CarePlanDraftVO d,Item i,String key,String kind) {
        var payload=JsonValues.tree(Map.of("schemaVersion","plan-conditions-v1","kind",kind,"itemId",i.id(),"actorType",i.actorType(),"required",true,"capability","MEDICATION_SOURCE".equals(kind)?"PLAN_CONFIRM_PROFESSIONAL":"PLAN_CONFIRM"));
        String hash=CanonicalJson.hash(JsonValues.tree(Map.of("planContentHash",d.contentHash(),"inputSnapshotId",d.inputSnapshotId(),"requirement",payload)));
        return new Requirement(key,kind,i.id(),i.actorType(),payload,hash);
    }
}
