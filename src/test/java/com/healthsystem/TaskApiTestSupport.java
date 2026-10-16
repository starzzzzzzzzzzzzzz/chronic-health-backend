package com.healthsystem;

import tools.jackson.databind.JsonNode;
import java.util.*;

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
}
