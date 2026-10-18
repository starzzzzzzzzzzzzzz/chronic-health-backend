package com.healthsystem;

import com.healthsystem.service.*;
import com.healthsystem.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"health.demo.enabled=true", "health.demo.token=development-test-token-only",
        "health.ai.enabled=true", "health.ai.api-key=test-placeholder-never-sent", "health.ai.worker-enabled=false",
        "health.legacy.recommendation-wait-ms=0"})
@Transactional
class LegacyRecommendationsApiTest extends DatabaseApiTestSupport {
    static final String ROUTE = "/store/care/drafts/recommendations/generate";
    @MockitoBean PlanAiClient client;
    @Autowired LegacyArchiveMappingService mappings;
    @Autowired PlanGenerationWorker worker;
    record Fixture(String patient, String alias, String fact) {}
    Fixture fixture() {
        String p = patient("demo-org", "DEMO", "旧建议生成虚构患者", true);
        String alias = mappings.ensureForPatient("demo-org", p).toString();
        String f = fact(p, "B", "routine", "{\"value\":\"晚睡自述\"}", null, null);
        return new Fixture(p, alias, f);
    }
    String body(Fixture f) { return "{\"archiveId\":" + f.alias() + ",\"supplementalRequirements\":\"先记录作息\"}"; }
    JsonNode call(String key, String body, int status) throws Exception {
        var response = mvc.perform(post(ROUTE).header("Authorization", TOKEN).header("X-Request-Id", key)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status)).andReturn().getResponse();
        String text = response.getContentAsString();
        assertThat(text).doesNotContain("test-placeholder-never-sent", "systemPrompt", "rawOutput");
        return json.readTree(text);
    }
    String job(String key) {
        return db.queryForObject("SELECT generation_job_id FROM legacy_recommendation_request WHERE org_id='demo-org' AND created_by='demo-clinician' AND request_key=?", String.class, key);
    }
    void answer(Fixture f) {
        when(client.generate(anyString(), anyString(), anyString())).thenReturn(new PlanAiClient.Result(
                AiPlanPolicyTest.candidate().replace("fact-1", f.fact()), "deepseek-flash", "legacy-test", "{}"));
    }

    @Test void timeoutReplayReturnsActualValidatedRecommendationsWithoutDoubleBillingOrAdoption() throws Exception {
        var f = fixture(); String key = UUID.randomUUID().toString(); answer(f);
        call(key, body(f), 504);
        String id = job(key);
        call(key, body(f), 504);
        assertThat(job(key)).isEqualTo(id);
        worker.process(id); worker.process(id);
        var response = call(key, body(f), 200);
        var data = response.path("data");
        assertThat(data.path("adviceId").asText()).isEqualTo(id);
        assertThat(data.path("generationStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(data.path("recommendations").size()).isEqualTo(1);
        var item = data.path("recommendations").get(0);
        assertThat(item.path("category").asText()).isEqualTo("起居");
        assertThat(item.path("evidenceCount").asInt()).isZero();
        assertThat(item.path("actions").isEmpty()).isTrue();
        assertThat(item.path("decompositionPending").asBoolean()).isTrue();
        assertThat(item.has("frequency")).isFalse();
        assertThat(data.has("knowledgeReleaseId")).isFalse();
        assertThat(call(key, body(f), 200)).isEqualTo(response);
        verify(client, times(1)).generate(anyString(), anyString(), anyString());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM care_plan WHERE patient_id=?", Integer.class, f.patient())).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM plan_generation_job WHERE patient_id=?", Integer.class, f.patient())).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM care_plan_item i JOIN care_plan_version v ON i.plan_version_id=v.id JOIN care_plan p ON v.plan_id=p.id WHERE p.patient_id=?", Integer.class, f.patient())).isZero();
        assertThat(db.queryForObject("SELECT status FROM plan_generation_job WHERE id=?", String.class, id)).isEqualTo("SUCCEEDED");
        JsonNode contract;
        try (var in = new ClassPathResource("contracts/legacy-recommendations.openapi.json").getInputStream()) { contract = json.readTree(in); }
        checkShape(response, contract.path("components").path("schemas").path("ResultGenerateRecommendationsResponse"), contract);
    }

    @Test void changedRequestCannotReuseKeyAndUnverifiedPreferencesNeverBecomeFacts() throws Exception {
        var f = fixture(); String key = UUID.randomUUID().toString();
        String body = "{\"archiveId\":\"" + f.alias() + "\",\"disease\":\"用户自称某病\",\"focuses\":[\"起居\"],\"prompt\":\"只记录\"}";
        call(key, body, 504);
        var input = json.readTree(db.queryForObject("SELECT model_input_json FROM plan_generation_job WHERE id=?", String.class, job(key)));
        assertThat(input.path("supplementalRequirements").asText()).contains("不能作为已确认病种", "用户自称某病", "起居", "只记录");
        assertThat(input.path("facts").toString()).doesNotContain("用户自称某病");
        call(key, body.replace("只记录", "其他要求"), 409);
        db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=?", f.patient());
        call(key, body, 403); verifyNoInteractions(client);
    }

    @Test void scopeAndUnsupportedManagementFailBeforeCreatingAnything() throws Exception {
        var f = fixture();
        for (String field : List.of("\"shopId\":999", "\"teamId\":999"))
            call(UUID.randomUUID().toString(), "{\"archiveId\":" + f.alias() + "," + field + "}", 403);
        call(UUID.randomUUID().toString(), "{\"archiveId\":" + f.alias() + ",\"confirmedManagement\":[{\"primarySubtype\":\"unsafe-unbound\"}]}", 400);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM care_plan WHERE patient_id=?", Integer.class, f.patient())).isZero();
        verifyNoInteractions(client);
    }

    @Test void numericAndStringArchiveIdsPreserveNineteenDigitsAndFractionsAreRejected() throws Exception {
        var f = fixture(); db.update("UPDATE legacy_archive_mapping SET archive_id=1000000000000000003 WHERE patient_id=?", f.patient());
        String key = UUID.randomUUID().toString();
        call(key, "{\"archiveId\":1000000000000000003}", 504);
        String id = job(key);
        call(key, "{\"archiveId\":\"1000000000000000003\"}", 504);
        assertThat(job(key)).isEqualTo(id);
        for (String value : List.of("1.5", "0", "-1", "9223372036854775808", "null"))
            call(UUID.randomUUID().toString(), "{\"archiveId\":" + value + "}", 400);
    }

    @Test void invalidProviderOutputAndProviderFailureNeverBecomeSuccessfulEmptyPlans() throws Exception {
        var f = fixture(); String key = UUID.randomUUID().toString();
        when(client.generate(anyString(), anyString(), anyString())).thenReturn(new PlanAiClient.Result("{}", "deepseek-flash", "bad", "{}"));
        call(key, body(f), 504); worker.process(job(key));
        assertThat(call(key, body(f), 502).path("message").asText()).contains("AI_OUTPUT_REJECTED");
        String next = UUID.randomUUID().toString();
        when(client.generate(anyString(), anyString(), anyString())).thenThrow(new DeepSeekPlanClient.AiFailure("AI_TIMEOUT"));
        call(next, body(f), 504); worker.process(job(next));
        assertThat(call(next, body(f), 504).path("message").asText()).contains("AI_TIMEOUT");
        call(next, body(f), 504); verify(client, times(2)).generate(anyString(), anyString(), anyString());
    }

    @Test void authenticationInvalidKeysAndMissingFactsDoNotQueueJobs() throws Exception {
        var f = fixture();
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON).content(body(f))).andExpect(status().isUnauthorized());
        for (String key : List.of(" ", "x".repeat(65))) call(key, body(f), 400);
        call(UUID.randomUUID().toString(), "{\"archiveId\":" + f.alias() + ",\"focuses\":[null]}", 400);
        String noFacts = patient("demo-org", "DEMO", "资料为空", true);
        String alias = mappings.ensureForPatient("demo-org", noFacts).toString();
        call(UUID.randomUUID().toString(), "{\"archiveId\":" + alias + "}", 400);
        // Test transaction is rolled back; successful requests are tested separately above.
        verifyNoInteractions(client);
    }

    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedSubmissionRollsBackDraftAndSnapshotOutsideTestTransaction() throws Exception {
        String p = patient("demo-org", "DEMO", "回滚验证虚构患者", true);
        try {
            // Mapper initialization needs a real transaction; the HTTP read path never creates aliases.
            db.update("INSERT INTO legacy_archive_mapping(org_id,patient_id,created_at) VALUES('demo-org',?,UTC_TIMESTAMP(6))", p);
            String alias = db.queryForObject("SELECT archive_id FROM legacy_archive_mapping WHERE patient_id=?", String.class, p);
            call(UUID.randomUUID().toString(), "{\"archiveId\":" + alias + "}", 400);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM care_plan WHERE patient_id=?", Integer.class, p)).isZero();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM health_input_snapshot WHERE patient_id=?", Integer.class, p)).isZero();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM plan_generation_job WHERE patient_id=?", Integer.class, p)).isZero();
            verifyNoInteractions(client);
        } finally {
            db.update("DELETE FROM legacy_archive_mapping WHERE patient_id=?", p);
            db.update("DELETE FROM patient_access_grant WHERE patient_id=?", p);
            db.update("DELETE FROM patient WHERE id=?", p);
        }
    }

    void checkShape(JsonNode value, JsonNode schema, JsonNode document) {
        if (schema.has("$ref")) { checkShape(value, document.at(schema.path("$ref").asText().substring(1)), document); return; }
        switch (schema.path("type").asText()) {
            case "object" -> {
                assertThat(value.isObject()).isTrue();
                for (var field : value.properties()) {
                    assertThat(schema.path("properties").has(field.getKey())).as(field.getKey()).isTrue();
                    checkShape(field.getValue(), schema.path("properties").path(field.getKey()), document);
                }
            }
            case "array" -> { assertThat(value.isArray()).isTrue(); for (var item : value) checkShape(item, schema.path("items"), document); }
            case "string" -> assertThat(value.isString()).isTrue();
            case "integer" -> assertThat(value.isIntegralNumber()).isTrue();
            case "boolean" -> assertThat(value.isBoolean()).isTrue();
            default -> fail("Unsupported frozen schema: " + schema);
        }
    }
}
