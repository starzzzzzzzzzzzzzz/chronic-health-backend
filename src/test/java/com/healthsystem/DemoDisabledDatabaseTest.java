package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest(properties={"health.demo.enabled=true","health.demo.token=development-test-token-only"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class DemoDisabledDatabaseTest {
 @Autowired MockMvc mvc;
 @Test void nonLocalProfileNeverAcceptsDemoToken() throws Exception {
  mvc.perform(get("/patients").header("Authorization","development-test-token-only")).andExpect(status().isUnauthorized());
 }
}
