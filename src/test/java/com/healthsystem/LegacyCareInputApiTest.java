package com.healthsystem;

import com.healthsystem.service.LegacyArchiveMappingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class LegacyCareInputApiTest extends DatabaseApiTestSupport {
    @Autowired LegacyArchiveMappingService mappings;
    String route(String id) { return "/store/care/archives/" + id + "/input"; }

    @Test void selectedArchiveLoadsCurrentFactsAndUnknownAxesWithoutCreatingSnapshots() throws Exception {
        String p = patient("demo-org", "DEMO", "旧接口输入虚构患者", true);
        mappings.ensureForPatient("demo-org", p);
        db.update("UPDATE legacy_archive_mapping SET archive_id=1000000000000000002 WHERE patient_id=?", p);
        String old = fact(p, "B", "sleep", "{\"value\":\"旧记录\"}", null, null);
        String current = fact(p, "B", "sleep", "{\"value\":\"当前晚睡自述\"}", old, null);
        var before = counts();
        String response = mvc.perform(get(route("1000000000000000002")).header("Authorization", TOKEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var data = json.readTree(response).path("data");
        assertThat(data.path("scope").path("archiveId").asText()).isEqualTo("1000000000000000002");
        assertThat(data.path("profileRevision").asText()).isEqualTo("1");
        assertThat(data.path("facts").size()).isEqualTo(1);
        assertThat(data.path("facts").get(0).path("factId").asText()).isEqualTo(current);
        assertThat(data.path("facts").get(0).path("status").asText()).isEqualTo("REPORTED");
        assertThat(data.path("facts").get(0).path("sourceType").asText()).isEqualTo("DEMO");
        assertThat(data.path("axes").size()).isEqualTo(6);
        assertThat(data.path("axes").path("H").path("status").asText()).isEqualTo("UNKNOWN");
        assertThat(data.path("missingFields").size()).isEqualTo(5);
        assertThat(data.path("patient").has("birthDate")).isFalse();
        assertThat(response).doesNotContain(old, "systemPrompt", TOKEN);
        assertThat(counts()).isEqualTo(before);
    }

    @Test void aliasesNeverBypassPatientScopeAndGrants() throws Exception {
        String allowed = patient("demo-org", "DEMO", "allowed", true);
        String alias = mappings.ensureForPatient("demo-org", allowed).toString();
        mvc.perform(get(route(alias))).andExpect(status().isUnauthorized());
        db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=?", allowed);
        mvc.perform(get(route(alias)).header("Authorization", TOKEN)).andExpect(status().isForbidden());
        for (String[] scope : new String[][]{{"other-org", "DEMO"}, {"demo-org", "REAL"}}) {
            String p = patient(scope[0], scope[1], "invisible", true);
            String id = mappings.ensureForPatient(scope[0], p).toString();
            mvc.perform(get(route(id)).header("Authorization", TOKEN).param("orgId", scope[0]).param("dataMode", scope[1]))
                    .andExpect(status().isNotFound());
        }
        String inactive = patient("demo-org", "DEMO", "inactive", true);
        String id = mappings.ensureForPatient("demo-org", inactive).toString();
        db.update("UPDATE patient SET status='INACTIVE' WHERE id=?", inactive);
        mvc.perform(get(route(id)).header("Authorization", TOKEN)).andExpect(status().isNotFound());
    }

    @Test void malformedAliasesAndUnimplementedWritesAreRejected() throws Exception {
        for (String id : List.of("0", "-1", "01", "1.5", "9223372036854775808", UUID.randomUUID().toString()))
            mvc.perform(get(route(id)).header("Authorization", TOKEN)).andExpect(status().isBadRequest());
        mvc.perform(get(route("9223372036854775807")).header("Authorization", TOKEN)).andExpect(status().isNotFound());
        mvc.perform(post(route("1")).header("Authorization", TOKEN)).andExpect(status().isForbidden());
    }

    List<Long> counts() {
        return List.of("patient", "health_fact", "health_input_snapshot", "legacy_archive_mapping").stream()
                .map(table -> db.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).toList();
    }
}
