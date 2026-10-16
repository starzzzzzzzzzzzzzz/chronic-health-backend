package com.healthsystem.dto;
import tools.jackson.databind.JsonNode;
import java.util.Set;
import static com.healthsystem.service.impl.PlanDraftValidation.*;
public final class WorkflowInput {
    private WorkflowInput() {}
    public static void fields(JsonNode node,String... allowed) {
        check(node!=null && node.isObject(),"Expected an object");
        var names=Set.of(allowed);
        for(String name:node.propertyNames()) check(names.contains(name),"Unknown request field: "+name);
    }
    public static String string(JsonNode node,String name,boolean required,int max) {
        var value=node.get(name);
        check(value==null || value.isNull() || value.isString(),"Invalid "+name);
        return text(value==null || value.isNull()?null:value.asText(),required,max);
    }
    public static long expected(JsonNode node){return revision(string(node,"expectedRevision",true,19));}
}
