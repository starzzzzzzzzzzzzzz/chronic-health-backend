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
        assertThat(doc.path("paths").size()).isEqualTo(12);
        int operations=0;
        for(var path:doc.path("paths")) {
            for(String method:java.util.List.of("get","post","patch"))if(path.has(method))operations++;
        }
        assertThat(operations).isEqualTo(14); // GET/PATCH and GET/POST share two resource paths.
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
    JsonNode document() throws Exception {
        return json.readTree(mvc.perform(get("/v3/api-docs/health")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
