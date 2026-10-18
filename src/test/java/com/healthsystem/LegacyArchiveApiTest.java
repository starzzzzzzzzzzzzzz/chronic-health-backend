package com.healthsystem;

import com.healthsystem.service.LegacyArchiveMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@Import(LegacyArchiveApiTest.ClockConfiguration.class)
class LegacyArchiveApiTest extends DatabaseApiTestSupport {
    static final String ROUTE="/store/care/grant-administration/archives";
    @TestConfiguration static class ClockConfiguration {
        @Bean @Primary MutableWorkflowClock legacyTestClock() { return new MutableWorkflowClock(); }
    }
    @Autowired MutableWorkflowClock clock;
    @Autowired LegacyArchiveMappingService mappings;
    String prefix;
    @BeforeEach void setup() {prefix="legacy-"+UUID.randomUUID();clock.set("2026-10-17T15:59:59Z");}

    @Test void oldContractDefaultsAndNineteenDigitIdsRemainExact() throws Exception {
        String id=archive(prefix,true);
        db.update("UPDATE legacy_archive_mapping SET archive_id=1000000000000000001 WHERE patient_id=?",id);
        var response=read(prefix);
        var data=response.path("data");
        assertThat(response.path("code").asInt()).isEqualTo(200);
        assertThat(response.path("message").asText()).isEqualTo("success");
        assertThat(data.path("page").asInt()).isEqualTo(1);
        assertThat(data.path("pageSize").asInt()).isEqualTo(10);
        assertThat(data.path("total").asInt()).isEqualTo(1);
        var item=data.path("items").get(0);
        assertThat(item.path("archiveId").asText()).isEqualTo("1000000000000000001");
        assertThat(item.path("name").asText()).isEqualTo(prefix);
        assertThat(item.path("diseaseTags").asText()).isEmpty();
        assertThat(item.has("age")).isFalse();
        assertThat(item.has("id")).isFalse();
        assertThat(data.has("size")).isFalse();
        var fixture=legacyContract();
        checkShape(response,fixture.path("components").path("schemas").path("ResultArchivePage"),fixture);
        assertThat(read(prefix).path("data").path("items").get(0).path("archiveId").asText())
                .isEqualTo("1000000000000000001");
    }

    @Test void paginationKeepsAuthorizedTotalStableAndEmptyPagesValid() throws Exception {
        for(int i=0;i<3;i++) {
            String id=archive(prefix+"-"+i,true);
            db.update("UPDATE patient SET created_at=? WHERE id=?","2020-01-0"+(i+1)+" 00:00:00",id);
        }
        var first=readPage(prefix,1,2).path("data");
        assertThat(first.path("total").asInt()).isEqualTo(3);
        assertThat(first.path("items").size()).isEqualTo(2);
        assertThat(first.path("items").get(0).path("name").asText()).isEqualTo(prefix+"-2");
        assertThat(first.path("pageSize").asInt()).isEqualTo(2);
        var second=readPage(prefix,2,2).path("data");
        assertThat(second.path("items").size()).isEqualTo(1);
        assertThat(second.path("items").get(0).path("name").asText()).isEqualTo(prefix+"-0");
        assertThat(second.path("total").asInt()).isEqualTo(3);
        assertThat(readPage(prefix,3,2).path("data").path("items").isEmpty()).isTrue();
        var empty=read(prefix+"-absent").path("data");
        assertThat(empty.path("total").asLong()).isZero();
        assertThat(empty.path("items").isArray()).isTrue();
        assertThat(empty.path("items").isEmpty()).isTrue();
    }

    @Test void keywordTreatsWildcardsLiterallyAndAbsentKeywordUsesEmptyDefault() throws Exception {
        archive(prefix+"%_=",true);archive(prefix+"abc",true);
        var literal=read(prefix+"%_=").path("data");
        assertThat(literal.path("total").asInt()).isEqualTo(1);
        assertThat(literal.path("items").get(0).path("name").asText()).isEqualTo(prefix+"%_=");
        var absent=json.readTree(mvc.perform(get(ROUTE).header("Authorization",TOKEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var explicit=read("");
        assertThat(absent).isEqualTo(explicit);
    }

    @Test void invalidPaginationAndKeywordsAreRejected() throws Exception {
        for(String[] pair:new String[][]{{"page","0"},{"page","-1"},{"page","no"},{"page","2147483648"},{"pageSize","0"},{"pageSize","101"},{"keyword","x".repeat(101)}}) {
            mvc.perform(get(ROUTE).header("Authorization",TOKEN).param(pair[0],pair[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
    }

    @Test void listCannotBroadenVisibilityBySupplyingIdentityOrMode() throws Exception {
        archive(prefix+"-allowed",true);
        patient("demo-org","DEMO",prefix+"-no-grant",false);
        patient("other-org","DEMO",prefix+"-other-org",true);
        patient("demo-org","REAL",prefix+"-real",true);
        String inactive=patient("demo-org","DEMO",prefix+"-inactive",true);
        db.update("UPDATE patient SET status='INACTIVE' WHERE id=?",inactive);
        String revoked=patient("demo-org","DEMO",prefix+"-revoked",true);
        db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=?",revoked);
        String expired=patient("demo-org","DEMO",prefix+"-expired",true);
        db.update("UPDATE patient_access_grant SET valid_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 HOUR) WHERE patient_id=?",expired);
        String future=patient("demo-org","DEMO",prefix+"-future-grant",true);
        db.update("UPDATE patient_access_grant SET valid_from=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY) WHERE patient_id=?",future);
        var data=json.readTree(mvc.perform(get(ROUTE).header("Authorization",TOKEN).param("keyword",prefix)
                .param("orgId","other-org").param("subject","admin").param("role","admin").param("dataMode","REAL"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(data.path("total").asInt()).isEqualTo(1);
        assertThat(data.path("items").get(0).path("name").asText()).isEqualTo(prefix+"-allowed");
    }

    @Test void onlyImplementedGetIsAuthenticatedAndOtherLegacyRoutesStayClosed() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROUTE).header("Authorization","wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post(ROUTE).header("Authorization",TOKEN)).andExpect(status().isForbidden());
        mvc.perform(get(ROUTE+"/123").header("Authorization",TOKEN)).andExpect(status().isForbidden());
    }

    @Test void birthdayUsesShanghaiDateAndUnknownOrFutureDatesAreOmitted() throws Exception {
        String birthday=archive(prefix+"-birthday",true);
        db.update("UPDATE patient SET birth_date='2000-10-18' WHERE id=?",birthday);
        assertThat(read(prefix+"-birthday").path("data").path("items").get(0).path("age").asInt()).isEqualTo(25);
        clock.set("2026-10-17T16:00:00Z");
        var response=read(prefix+"-birthday");
        assertThat(response.path("data").path("items").get(0).path("age").asInt()).isEqualTo(26);
        var fixture=legacyContract();
        checkShape(response,fixture.path("components").path("schemas").path("ResultArchivePage"),fixture);
        String future=archive(prefix+"-future-birth",true);
        db.update("UPDATE patient SET birth_date='2026-10-19' WHERE id=?",future);
        assertThat(read(prefix+"-future-birth").path("data").path("items").get(0).has("age")).isFalse();
        archive(prefix+"-unknown",true);
        assertThat(read(prefix+"-unknown").path("data").path("items").get(0).has("age")).isFalse();
    }

    @Test void getsHaveNoWriteSideEffectsAndMissingMappingFailsExplicitly() throws Exception {
        archive(prefix+"-mapped",true);
        var before=counts();read(prefix+"-mapped");assertThat(counts()).isEqualTo(before);
        patient("demo-org","DEMO",prefix+"-unmapped",true);
        before=counts();
        mvc.perform(get(ROUTE).header("Authorization",TOKEN).param("keyword",prefix))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
        assertThat(counts()).isEqualTo(before);
    }

    @Test void corruptNegativeAliasIsNotExposedToFrontend() throws Exception {
        String id=archive(prefix,true);
        db.update("UPDATE legacy_archive_mapping SET archive_id=-1 WHERE patient_id=?",id);
        mvc.perform(get(ROUTE).header("Authorization",TOKEN).param("keyword",prefix))
                .andExpect(status().isConflict());
    }

    String archive(String name,boolean grant) {
        String id=patient("demo-org","DEMO",name,grant);
        mappings.ensureForPatient("demo-org",id);return id;
    }
    JsonNode read(String keyword) throws Exception {
        return json.readTree(mvc.perform(get(ROUTE).header("Authorization",TOKEN).param("keyword",keyword))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode readPage(String keyword,int page,int size) throws Exception {
        return json.readTree(mvc.perform(get(ROUTE).header("Authorization",TOKEN).param("keyword",keyword)
                        .param("page",Integer.toString(page)).param("pageSize",Integer.toString(size)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    List<Long> counts() {
        return List.of("patient","patient_access_grant","legacy_archive_mapping","health_input_snapshot").stream()
                .map(t->db.queryForObject("SELECT COUNT(*) FROM "+t,Long.class)).toList();
    }
    JsonNode legacyContract() throws Exception {
        try(var in=new ClassPathResource("contracts/legacy-archives.openapi.json").getInputStream()) {return json.readTree(in);}
    }
    // Validates the deliberately small frozen schema vocabulary; fail on unsupported types.
    void checkShape(JsonNode value,JsonNode schema,JsonNode document) {
        if(schema.has("$ref")) {checkShape(value,document.at(schema.path("$ref").asText().substring(1)),document);return;}
        switch(schema.path("type").asText()) {
            case "object" -> {
                assertThat(value.isObject()).isTrue();
                for(var field:value.properties()) {
                    assertThat(schema.path("properties").has(field.getKey())).as(field.getKey()).isTrue();
                    checkShape(field.getValue(),schema.path("properties").path(field.getKey()),document);
                }
            }
            case "array" -> {assertThat(value.isArray()).isTrue();for(var item:value)checkShape(item,schema.path("items"),document);}
            case "string" -> assertThat(value.isString()).isTrue();
            case "integer" -> assertThat(value.isIntegralNumber()).isTrue();
            default -> fail("Unsupported frozen schema: "+schema);
        }
    }
}
