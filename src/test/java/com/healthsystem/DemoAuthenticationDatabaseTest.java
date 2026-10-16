package com.healthsystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"health.demo.enabled=true","health.demo.token=development-test-token-only"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(DemoAuthenticationDatabaseTest.Probe.class)
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class DemoAuthenticationDatabaseTest {
 @Autowired MockMvc mvc;
 @RestController static class Probe {
  @GetMapping("/patients/__identity") String identity(Authentication auth) { return auth.getName(); }
 }
 @Test void explicitlyConfiguredTokenUsesServerSideIdentity() throws Exception {
  mvc.perform(get("/patients/__identity").header("Authorization","development-test-token-only").param("orgId","attacker-org"))
   .andExpect(status().isOk()).andExpect(content().string("demo-clinician"));
 }
 @Test void invalidOrAbsentTokensAreRejected() throws Exception {
  mvc.perform(get("/patients/__identity")).andExpect(status().isUnauthorized());
  mvc.perform(get("/patients/__identity").header("Authorization","wrong")).andExpect(status().isUnauthorized());
 }
}
