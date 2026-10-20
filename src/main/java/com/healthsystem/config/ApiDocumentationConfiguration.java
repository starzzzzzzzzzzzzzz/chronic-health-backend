package com.healthsystem.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import java.util.*;

@Configuration
@Profile("local")
@ConditionalOnProperty(name="health.docs.enabled",havingValue="true")
public class ApiDocumentationConfiguration {
    static {
        SpringDocUtils.getConfig().replaceWithClass(tools.jackson.databind.JsonNode.class,Object.class)
                .replaceWithClass(tools.jackson.databind.node.ObjectNode.class,Object.class);
    }
    @Bean OpenAPI healthOpenApi(@Value("${server.servlet.context-path:/ncd-ai}") String contextPath) {
        return new OpenAPI().info(new Info().title("慢病健康管理后端 API").version("0.3.0")
                .description("本地 DEMO：患者、方案与一审、七维任务与二审、发布、日任务与基础打卡。Authorize 中填写原始 Token，不加 Bearer。编辑身份使用 .env.demo 中 HEALTH_DEMO_API_TOKEN；审核身份使用 HEALTH_DEMO_REVIEWER_TOKEN。患者打卡使用 HEALTH_DEMO_PATIENT_TOKEN。计完成模式；可选 DeepSeek AI 方案候选，仅开放 DEMO 并需人工审核；无质量评分及消息发送。文档不包含凭证值。"))
                .servers(List.of(new Server().url(contextPath)))
                .components(new Components().addSecuritySchemes("AuthorizationToken",new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("Authorization")
                        .description("原始 DEMO Token，不加 Bearer。审核时切换为审核身份 Token；调试完成后在 Authorize 页面点击注销，清除本机浏览器保存的授权值。")))
                .addSecurityItem(new SecurityRequirement().addList("AuthorizationToken"));
    }
    @Bean GroupedOpenApi healthDocumentationGroup() {
        return GroupedOpenApi.builder().group("health").displayName("慢病健康管理")
                .packagesToScan("com.healthsystem.controller").build();
    }
    @Bean GlobalOpenApiCustomizer apiResponseDocumentation() {
        return api -> {
            var error=new ObjectSchema().addProperty("code",new IntegerSchema().example(400))
                    .addProperty("message",new StringSchema().example("请求参数不符合要求，请检查后重试"))
                    .addProperty("data",new Schema<>().nullable(true));
            api.getComponents().addSchemas("ApiError",error);
            // Jackson 3 JsonNode fields need explicit shapes after schema resolution.
            var schemas=api.getComponents().getSchemas();
            schemas.get("Item").addProperty("references",new ArraySchema().items(new ObjectSchema()).maxItems(30).nullable(true));
            schemas.get("Item").addProperty("contraindications",new ComposedSchema()
                    .oneOf(List.of(new ObjectSchema(),new ArraySchema().items(new Schema<>()),
                            new StringSchema().nullable(true)._enum(Collections.singletonList(null))))
                    .description("禁忌信息，可为 JSON 对象或数组"));
            schemas.get("Item").addProperty("professionalParameters",new ObjectSchema().maxProperties(0).nullable(true)
                    .description("当前阶段仅允许空对象或 null"));
            for(var entry:Map.of("FactVO",List.of("value","sourceRef"),"Condition",List.of("requirement"),"Record",List.of("requirement","value")).entrySet()) {
                var schema=schemas.get(entry.getKey());
                if(schema!=null)for(String field:entry.getValue())schema.addProperty(field,new ObjectSchema().nullable(true));
            }
            TaskApiDocumentation.apply(api);
            WorkflowResponseSchemas.apply(schemas);
            // Jackson 3 JsonNode is globally projected to Object; restore the strict confirmation array schema.
            io.swagger.v3.core.converter.ModelConverters.getInstance().readAll(com.healthsystem.dto.ConfirmedManagementRequest.class)
                    .forEach(schemas::putIfAbsent);
            for(String type:List.of("GeneratePlanRequest","LegacyGenerateRecommendationsRequest"))
                schemas.get(type).addProperty("confirmedManagement",new ArraySchema().items(new Schema<>().$ref("#/components/schemas/ConfirmedManagementRequest"))
                        .maxItems(20).nullable(true).description("来源绑定的人工侧重确认；evaluation/sourceHash 原样来自依据评估，权重为整数百分比"));
            api.getPaths().forEach((path,item)->item.readOperationsMap().forEach((method,operation)->{
                // Preserve standard OpenAPI examples and show a copyable body in Knife4j's description.
                if(operation.getRequestBody()!=null && operation.getRequestBody().getContent()!=null) {
                    operation.getRequestBody().getContent().values().forEach(media->{
                        if(media.getExample()==null && media.getExamples()!=null && media.getExamples().size()==1) {
                            media.setExample(media.getExamples().values().iterator().next().getValue());
                            media.setExamples(null);
                        }
                        // This UI version generates debug bodies from properties, ignoring request examples.
                        if(media.getExample()!=null) {
                            String example=io.swagger.v3.core.util.Json.pretty(media.getExample());
                            operation.setDescription(Objects.toString(operation.getDescription(),"")
                                    +"\n\n**可复制的请求示例**：将以下 JSON 复制到调试正文，替换实际 ID 和修订号。下方自动生成的完整结构仅供字段参考。\n\n```json\n"+example+"\n```");
                        }
                    });
                }
                if("/health".equals(path)){operation.setSecurity(List.of());return;}
                // Knife4j 4.5 reads operation security rather than inheriting the root declaration.
                operation.setSecurity(List.of(new SecurityRequirement().addList("AuthorizationToken")));
                ApiResponses responses=operation.getResponses();
                addError(responses,"401","需要有效认证");addError(responses,"403","缺少患者能力授权，或不允许自审/撤回");
                if(path.contains("{id}") || "/care-plans".equals(path))addError(responses,"404","目标不存在或机构/数据模式不可见");
                if(method==PathItem.HttpMethod.POST || method==PathItem.HttpMethod.PATCH){
                    addError(responses,"400","参数、依据或送审条件不满足");addError(responses,"409","修订过期、状态冲突或重复操作");
                } else if("/patients".equals(path))addError(responses,"400","分页或查询条件不合法");
                if("/store/care/grant-administration/archives".equals(path)) {
                    addError(responses,"400","分页或查询条件不合法");
                    addError(responses,"409","患者档案编号映射尚未准备");
                }
                if("/store/care/archives/{archiveId}/input".equals(path)) {
                    addError(responses,"400","档案编号不合法");addError(responses,"404","档案不存在或不可见");
                }
                if("/store/care/drafts/recommendations/generate".equals(path)) {
                    addError(responses,"404","档案不存在或不可见");addError(responses,"429","生成队列已满");
                    addError(responses,"502","AI 输出校验失败或模型服务失败");addError(responses,"503","AI 未配置或等待中断");
                    addError(responses,"504","等待超时；保留原 X-Request-Id 和请求体重试，避免重复生成");
                }
            }));
        };
    }
    private static void addError(ApiResponses responses,String code,String description) {
        responses.addApiResponse(code,new io.swagger.v3.oas.models.responses.ApiResponse().description(description)
                .content(new Content().addMediaType("application/json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
    }
}
