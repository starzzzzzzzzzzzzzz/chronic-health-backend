package com.healthsystem;

import com.healthsystem.common.web.RequestIdFilter;
import com.healthsystem.config.SecurityConfiguration;
import com.healthsystem.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@Import({SecurityConfiguration.class, RequestIdFilter.class})
class WebFoundationTest {
    @Autowired MockMvc mvc;

    @Test
    void healthReturnsContractAndPreservesValidTraceId() throws Exception {
        mvc.perform(get("/health").header("X-Request-Id", "health-check-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "health-check-001"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("UP"));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void malformedTraceIdIsReplaced() throws Exception {
        String id = mvc.perform(get("/health").header("X-Request-Id", "a".repeat(100)))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(id).matches("[0-9a-f-]{36}");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void businessRoutesRejectUnverifiedTokens() throws Exception {
        mvc.perform(get("/app/archive/1").header("Authorization", "not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void actuatorInternalsAreNotPublic() throws Exception {
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
