package com.healthsystem;

import tools.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class TaskApiTestSupport extends ReviewApiTestSupport {
    Fixture approved(String dimension,boolean execution) throws Exception {
        var f=draft(dimension,execution);
        for(String cap:List.of("TASK_EDIT","TASK_PUBLISH","TASK_READ"))grant(f.patient(),"demo-clinician",cap);
        for(String cap:List.of("TASK_REVIEW","TASK_READ"))grant(f.patient(),"demo-reviewer",cap);
        if(execution)confirm(f,"1");
        var submission=submit(f,execution?"2":"1");
        postJson("/review-submissions/"+submission.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),201);
        return f;
    }
    JsonNode bundle(Fixture f,String revision) throws Exception {
        return postJson("/plan-versions/"+f.version()+"/task-bundles",TOKEN,Map.of("expectedRevision",revision),201);
    }
    JsonNode editBundle(String id,Object body,int expected) throws Exception {
        var response=mvc.perform(patch("/task-bundles/"+id).header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expected)).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("data");
    }
    Map<String,Object> slot(String key,String time,int open,int close){return Map.of("slotKey",key,"mode","FIXED","localTime",time,"opensOffsetMinutes",open,"closesOffsetMinutes",close);}
    JsonNode configure(JsonNode b,List<Map<String,Object>> slots) throws Exception {
        return editBundle(b.path("id").asText(),Map.of("expectedRevision",b.path("rowVersion").asText(),"groups",List.of(Map.of("id",b.path("groups").get(0).path("id").asText(),"executionConfirmed",true,"confirmationNote","已人工核对执行动作与目标", "schedule",Map.of("isoWeekdays",List.of(1,2,3,4,5,6,7),"slots",slots,"rotationPolicy","FIXED")))),200);
    }
    JsonNode preview(JsonNode b) throws Exception {return postJson("/task-bundles/"+b.path("id").asText()+"/schedule-previews",TOKEN,Map.of("expectedRevision",b.path("rowVersion").asText()),201);}
    JsonNode readyBundle() throws Exception {var f=approved("routine",true);return configure(bundle(f,"4"),List.of(slot("morning","08:00",0,60)));}
    Fixture approvedPeriod(String dimension,String start,String end,String zone) throws Exception {
        var f=draft(dimension,true);
        for(String cap:List.of("TASK_EDIT","TASK_PUBLISH","TASK_READ"))grant(f.patient(),"demo-clinician",cap);
        for(String cap:List.of("TASK_REVIEW","TASK_READ"))grant(f.patient(),"demo-reviewer",cap);
        var changed=patchJson(f.version(),Map.of("expectedRevision","1","startDate",start,"endDate",end,"zoneId",zone),200);
        var confirmed=confirm(f,changed.path("rowVersion").asText());
        var submission=submit(f,confirmed.path("rowVersion").asText());
        postJson("/review-submissions/"+submission.path("id").asText()+"/decisions",REVIEWER,Map.of("expectedRevision","0","decision","APPROVE"),201);return f;
    }
    JsonNode generateCurrent(Fixture f) throws Exception {return bundle(f,getJson("/plan-versions/"+f.version(),TOKEN,200).path("rowVersion").asText());}
}
