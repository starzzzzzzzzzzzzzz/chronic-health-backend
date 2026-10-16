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
 @Autowired org.springframework.jdbc.core.JdbcTemplate db;
 @Autowired com.healthsystem.service.impl.DemoDataInitializer initializer;
 @RestController static class Probe {
  @GetMapping("/patients/__identity") String identity(Authentication auth) { return auth.getName(); }
 }
 @Test void explicitlyConfiguredTokenUsesServerSideIdentity() throws Exception {
  mvc.perform(get("/patients/__identity").header("Authorization","development-test-token-only").param("orgId","attacker-org"))
   .andExpect(status().isOk()).andExpect(content().string("demo-clinician"));
 }
 @Test @org.springframework.transaction.annotation.Transactional
 void restartingDemoInitializationPreservesEditsAndRevokedGrants() throws Exception {
  String id="00000000-0000-4000-8000-000000000001";
  int count=db.queryForObject("SELECT COUNT(*) FROM health_fact WHERE patient_id=?",Integer.class,id);
  db.update("UPDATE patient SET name='保留人工修改的虚构姓名' WHERE id=?",id);
  db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=?",id);
  initializer.run(null);
  org.assertj.core.api.Assertions.assertThat(db.queryForObject("SELECT name FROM patient WHERE id=?",String.class,id)).isEqualTo("保留人工修改的虚构姓名");
  org.assertj.core.api.Assertions.assertThat(db.queryForObject("SELECT COUNT(*) FROM health_fact WHERE patient_id=?",Integer.class,id)).isEqualTo(count);
  org.assertj.core.api.Assertions.assertThat(db.queryForObject("SELECT COUNT(*) FROM patient_access_grant WHERE patient_id=? AND revoked_at IS NULL",Integer.class,id)).isZero();
 }
 @Test void invalidOrAbsentTokensAreRejected() throws Exception {
  mvc.perform(get("/patients/__identity")).andExpect(status().isUnauthorized());
  mvc.perform(get("/patients/__identity").header("Authorization","wrong")).andExpect(status().isUnauthorized());
 }
}
