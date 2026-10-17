package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class ApiDocumentationDatabaseTest extends DatabaseApiTestSupport {
    @Test void localDocumentationPageAndDiscoveryLoadWithoutUnlockingBusinessEndpoints() throws Exception {
        mvc.perform(get("/doc.html")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("<html")));
        var config=json.readTree(mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(config.path("urls").size()).isEqualTo(1);
        assertThat(config.path("urls").get(0).path("url").asText()).endsWith("/v3/api-docs/health");
        mvc.perform(get("/patients")).andExpect(status().isUnauthorized());
        mvc.perform(get("/patients").header("Authorization",TOKEN)).andExpect(status().isOk());
    }
    @Test void generatedContractListsCurrentRoutesAndRawAuthorizationHeader() throws Exception {
        var doc=document();
        assertThat(doc.path("openapi").asText()).startsWith("3.0.");
        assertThat(doc.path("paths").size()).isEqualTo(28);
        int operations=0;
        for(var path:doc.path("paths")) {
            for(String method:java.util.List.of("get","post","patch"))if(path.has(method))operations++;
        }
        assertThat(operations).isEqualTo(32); // Multiple methods can share one resource path.
        var scheme=doc.path("components").path("securitySchemes").path("AuthorizationToken");
        assertThat(scheme.path("type").asText()).isEqualTo("apiKey");
        assertThat(scheme.path("name").asText()).isEqualTo("Authorization");
        assertThat(scheme.path("in").asText()).isEqualTo("header");
        assertThat(doc.path("servers").get(0).path("url").asText()).isEqualTo("/ncd-ai");
        assertThat(doc.toString()).doesNotContain("BusinessPrincipal", "subjectId",TOKEN,"DEMO_API_TOKEN_VALUE");
        assertThat(doc.path("paths").path("/care-plans").path("post").path("responses").has("201")).isTrue();
        assertThat(doc.path("paths").path("/patients").path("get").path("responses").has("401")).isTrue();
        assertThat(doc.path("paths").path("/patients").path("get").path("security").path(0).has("AuthorizationToken")).isTrue();
        assertThat(doc.path("paths").path("/review-submissions/{id}/decisions").path("post").path("responses").has("409")).isTrue();
        assertThat(doc.path("paths").path("/health").path("get").has("security")).isTrue();
        assertThat(doc.path("paths").path("/health").path("get").path("security").isEmpty()).isTrue();
    }
    @Test
    @org.springframework.transaction.annotation.Transactional
    void patientResponsesAllowUnknownBirthDateInBothPayloadAndContract() throws Exception {
        String name="nullable-birth-date-"+java.util.UUID.randomUUID();
        String id=patient("demo-org","DEMO",name,true);
        var list=json.readTree(mvc.perform(get("/patients").header("Authorization",TOKEN).param("q",name))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list.path("data").path("items").size()).isEqualTo(1);
        assertThat(list.path("data").path("items").get(0).path("birthDate").isNull()).isTrue();
        var summary=json.readTree(mvc.perform(get("/patients/"+id+"/health-summary").header("Authorization",TOKEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(summary.path("data").path("patient").path("birthDate").isNull()).isTrue();
        var birthDate=document().path("components").path("schemas").path("PatientSummaryVO")
                .path("properties").path("birthDate");
        assertThat(birthDate.path("type").asText()).isEqualTo("string");
        assertThat(birthDate.path("format").asText()).isEqualTo("date");
        assertThat(birthDate.path("nullable").asBoolean()).as("unknown birth dates are returned as null").isTrue();
    }
    @Test void flexibleWorkflowBodiesHaveRealRootFieldsAndExamples() throws Exception {
        var doc=document();
        var item=doc.path("components").path("schemas").path("Item").path("properties");
        assertThat(item.path("references").path("type").asText()).isEqualTo("array");
        assertThat(doc.path("components").path("schemas").path("FactVO").path("properties").path("value").has("type")).isFalse();
        String[][] cases={{"/plan-versions/{id}","patch","expectedRevision"},{"/plan-versions/{id}/condition-confirmations","post","entries"},{"/plan-versions/{id}/review-submissions","post","expectedRevision"},{"/review-submissions/{id}/decisions","post","decision"},{"/review-submissions/{id}/withdrawals","post","reason"},{"/care-plans/{id}/versions","post","sourceVersionId"}};
        for(var row:cases) {
            var operation=doc.path("paths").path(row[0]).path(row[1]);
            assertThat(operation.path("summary").asText()).isNotBlank();
            var media=operation.path("requestBody").path("content").path("application/json");
            var schema=media.path("schema");if(schema.has("$ref"))schema=doc.path("components").path("schemas").path(schema.path("$ref").asText().replace("#/components/schemas/",""));
            assertThat(schema.path("properties").has(row[2])).as(row[0]).isTrue();
            assertThat(schema.path("properties").has("body")).isFalse();
            assertThat(schema.path("properties").has("changes")).isFalse();
            assertThat(media.path("example").isMissingNode()).as("Knife4j single example: "+row[0]).isFalse();
            assertThat(operation.path("description").asText()).contains("可复制的请求示例", "```json", row[2]);
        }
    }
    @Test void taskWorkflowDocumentationHasEditableRootBodiesAndPatientIdentityGuidance() throws Exception {
        var doc=document();
        String[][] cases={{"/plan-versions/{id}/task-bundles","post","expectedRevision"},{"/task-bundles/{id}","patch","groups"},{"/task-bundles/{id}/schedule-previews","post","expectedRevision"},{"/task-bundles/{id}/review-submissions","post","schedulePreviewId"},{"/task-bundles/{id}/versions","post","changeReason"},{"/task-bundles/{id}/publication-previews","post","replacementScope"},{"/task-bundles/{id}/publications","post","publicationPreviewId"},{"/execution-tasks/{id}/check-ins","post","completion"},{"/check-ins/{id}/amendments","post","expectedRevision"}};
        for(var row:cases){var op=doc.path("paths").path(row[0]).path(row[1]);var media=op.path("requestBody").path("content").path("application/json");var schema=media.path("schema");if(schema.has("$ref"))schema=doc.path("components").path("schemas").path(schema.path("$ref").asText().replace("#/components/schemas/",""));assertThat(schema.path("properties").has(row[2])).as(row[0]).isTrue();assertThat(schema.path("properties").has("body")).isFalse();assertThat(media.has("example")).isTrue();assertThat(op.path("description").asText()).contains("可复制的请求示例");}
        assertThat(doc.path("info").path("description").asText()).contains("HEALTH_DEMO_PATIENT_TOKEN");
        var response=doc.path("paths").path("/execution-tasks/{id}/check-ins").path("post").path("responses").path("201").path("content").path("application/json").path("schema");
        assertThat(response.has("$ref")).as("Knife4j needs a named response envelope to render nested feedback fields").isTrue();
        for(String route:java.util.List.of("/task-bundles/{id}/publications","/execution-tasks/{id}/check-ins","/check-ins/{id}/amendments")){var op=doc.path("paths").path(route).path("post");assertThat(op.path("responses").has("200")).isTrue();assertThat(op.path("parameters").toString()).contains("Idempotency-Key");}
    }
    JsonNode document() throws Exception {
        return json.readTree(mvc.perform(get("/v3/api-docs/health")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
