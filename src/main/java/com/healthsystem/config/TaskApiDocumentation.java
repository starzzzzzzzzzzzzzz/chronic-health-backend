package com.healthsystem.config;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.core.converter.*;
import java.util.*;
import static com.healthsystem.config.TaskApiDocumentationModels.*;
/** Adds concrete task schemas before the shared example/authorization customizer runs. */
final class TaskApiDocumentation {
 private TaskApiDocumentation(){}
 static void apply(OpenAPI api) {
  var revision=Map.of("expectedRevision","0");String uuid="00000000-0000-0000-0000-000000000001";
  var slot=Map.of("slotKey","morning","mode","FIXED","localTime","08:00","opensOffsetMinutes",0,"closesOffsetMinutes",60);
  body(api,"/plan-versions/{id}/task-bundles",PathItem.HttpMethod.POST,Revision.class,revision);
  body(api,"/task-bundles/{id}",PathItem.HttpMethod.PATCH,BundlePatch.class,Map.of("expectedRevision","0","groups",List.of(Map.of("id",uuid,"executionConfirmed",true,"confirmationNote","已人工核对来源动作与执行条件","schedule",Map.of("isoWeekdays",List.of(1,2,3,4,5,6,7),"slots",List.of(slot),"rotationPolicy","FIXED")))));
  body(api,"/task-bundles/{id}/schedule-previews",PathItem.HttpMethod.POST,Revision.class,Map.of("expectedRevision","1"));
  body(api,"/task-bundles/{id}/review-submissions",PathItem.HttpMethod.POST,TaskReview.class,Map.of("expectedRevision","2","schedulePreviewId",uuid));
  body(api,"/task-bundles/{id}/versions",PathItem.HttpMethod.POST,NewVersion.class,Map.of("expectedRevision","4","changeReason","调整执行编排并重新二审"));
  var intent=Map.of("expectedRevision","4","reviewSubmissionId",uuid,"schedulePreviewId",uuid,"effectiveAt","2026-10-17T00:00:00Z","replacementScope","NONE");
  body(api,"/task-bundles/{id}/publication-previews",PathItem.HttpMethod.POST,PublicationIntent.class,intent);
  var publishing=new LinkedHashMap<String,Object>(intent);publishing.put("publicationPreviewId",uuid);
  body(api,"/task-bundles/{id}/publications",PathItem.HttpMethod.POST,PublicationRequest.class,publishing);
  body(api,"/execution-tasks/{id}/check-ins",PathItem.HttpMethod.POST,Feedback.class,Map.of("completion","COMPLETED"));
  body(api,"/check-ins/{id}/amendments",PathItem.HttpMethod.POST,Amendment.class,Map.of("expectedRevision","0","note","补充自述执行情况"));
  response(api,"/task-bundles/{id}/publication-previews",PathItem.HttpMethod.POST,PublicationPreviewView.class,false);
  response(api,"/task-bundles/{id}/publication-previews/{previewId}",PathItem.HttpMethod.GET,PublicationPreviewView.class,false);
  response(api,"/task-bundles/{id}/publications",PathItem.HttpMethod.POST,PublicationView.class,false);
  response(api,"/publications/{id}",PathItem.HttpMethod.GET,PublicationView.class,false);
  response(api,"/patients/{id}/execution-tasks",PathItem.HttpMethod.GET,ExecutionView.class,true);
  response(api,"/execution-tasks/{id}",PathItem.HttpMethod.GET,ExecutionView.class,false);
  response(api,"/execution-tasks/{id}/check-ins",PathItem.HttpMethod.POST,FeedbackView.class,false);
  response(api,"/execution-tasks/{id}/check-ins",PathItem.HttpMethod.GET,FeedbackView.class,true);
  response(api,"/check-ins/{id}/amendments",PathItem.HttpMethod.POST,FeedbackView.class,false);
  response(api,"/check-ins/{id}",PathItem.HttpMethod.GET,FeedbackView.class,false);
  var schemas=api.getComponents().getSchemas();
  if(schemas.containsKey("TaskBundleVO"))schemas.get("TaskBundleVO").addProperty("groups",new ArraySchema().items(schema(api,Group.class)));
  if(schemas.containsKey("TaskSchedulePreviewVO"))schemas.get("TaskSchedulePreviewVO").addProperty("occurrences",new ArraySchema().items(schema(api,Occurrence.class)));
  for(String path:List.of("/task-bundles/{id}/publications","/execution-tasks/{id}/check-ins","/check-ins/{id}/amendments")) {
   var op=api.getPaths().get(path).getPost();op.getResponses().addApiResponse("200",new io.swagger.v3.oas.models.responses.ApiResponse().description("同键同内容，回读既有资源").content(op.getResponses().get("201").getContent()));
  }
 }
 private static Schema<?> schema(OpenAPI api,Class<?> type) {var resolved=ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));resolved.referencedSchemas.forEach(api.getComponents()::addSchemas);return resolved.schema;}
 private static void body(OpenAPI api,String path,PathItem.HttpMethod method,Class<?> type,Object example){var op=api.getPaths().get(path).readOperationsMap().get(method);op.getRequestBody().setContent(new Content().addMediaType("application/json",new MediaType().schema(schema(api,type)).example(example)));}
 private static void response(OpenAPI api,String path,PathItem.HttpMethod method,Class<?> type,boolean array){var op=api.getPaths().get(path).readOperationsMap().get(method);Schema<?> data=schema(api,type);if(array)data=new ArraySchema().items(data);var envelope=new ObjectSchema().addProperty("code",new IntegerSchema()).addProperty("message",new StringSchema()).addProperty("data",data);String name="TaskApiResponse"+type.getSimpleName()+(array?"List":"");api.getComponents().addSchemas(name,envelope);for(var entry:op.getResponses().entrySet())if(entry.getKey().startsWith("2"))entry.getValue().setContent(new Content().addMediaType("application/json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/"+name))));}
}
