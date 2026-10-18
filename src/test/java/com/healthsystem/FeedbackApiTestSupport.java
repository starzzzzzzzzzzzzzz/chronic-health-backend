package com.healthsystem;
import java.util.*;
import tools.jackson.databind.JsonNode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
abstract class FeedbackApiTestSupport extends PublicationApiTestSupport {
 JsonNode publishedTask() throws Exception {
  var r=approvedTask();publish(r,prepare(r,"NONE","2026-10-16T00:00:00Z"),UUID.randomUUID().toString(),201);workflowClock.set("2026-10-17T00:30:00Z");
  return getJson("/patients/"+r.bundle().path("patientId").asText()+"/execution-tasks?date=2026-10-17",PATIENT,200).get(0);
 }
 JsonNode feedback(String route,String token,String key,Object body,int expected) throws Exception {
  var response=mvc.perform(post(route).header("Authorization",token).header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().is(expected)).andReturn().getResponse();
  if(expected==201)org.assertj.core.api.Assertions.assertThat(response.getHeader("Location")).contains("/check-ins/");return json.readTree(response.getContentAsString()).path("data");
 }
 String checkins(JsonNode task){return "/execution-tasks/"+task.path("id").asText()+"/check-ins";}
}
