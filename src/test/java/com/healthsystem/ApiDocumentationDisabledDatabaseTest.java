package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest(properties={"health.demo.enabled=true","health.demo.token=development-test-token-only","health.docs.enabled=false","springdoc.api-docs.enabled=true"})
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class ApiDocumentationDisabledDatabaseTest extends DatabaseApiTestSupport {
    @Test void localDocsSwitchCannotBeBypassedBySpringdocFlag() throws Exception {
        for(String path:new String[]{"/doc.html","/v3/api-docs","/v3/api-docs/health","/v3/api-docs/swagger-config","/swagger-ui/index.html","/webjars/knife4j/index.html"})
            mvc.perform(get(path).header("Authorization",TOKEN)).andExpect(status().isForbidden());
        mvc.perform(get("/patients").header("Authorization",TOKEN)).andExpect(status().isOk());
    }
}
