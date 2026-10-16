package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class PatientApiTest extends DatabaseApiTestSupport {
 @Test void listsOnlyGrantedDemoPatientsAndTreatsLikeCharactersLiterally() throws Exception {
  String prefix="case-"+java.util.UUID.randomUUID();
  String id=patient("demo-org","DEMO",prefix+"%_A",true);
  patient("demo-org","DEMO",prefix+"XYZ_A",true);
  patient("other-org","DEMO",prefix+"%_B",true);
  patient("demo-org","REAL",prefix+"%_C",true);
  patient("demo-org","DEMO",prefix+"%_D",false);
  mvc.perform(get("/patients").header("Authorization",TOKEN).param("q",prefix+"%_")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value(id));
 }
 @Test void paginationAndValidationUseHttpErrors() throws Exception {
  mvc.perform(get("/patients").header("Authorization",TOKEN).param("page","0")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
  mvc.perform(get("/patients").header("Authorization",TOKEN).param("size","101")).andExpect(status().isBadRequest());
  mvc.perform(get("/patients").header("Authorization",TOKEN).param("page","not-a-number")).andExpect(status().isBadRequest());
 }
 @Test void summaryKeepsUnknownAxesConflictsAndSnapshotReadHasNoSideEffect() throws Exception {
  String id=patient("demo-org","DEMO","资料测试虚构患者",true);
  String old=fact(id,"B","wake","{\"valueType\":\"TEXT\",\"value\":\"07:00\"}",null,null);
  fact(id,"B","wake","{\"valueType\":\"TEXT\",\"value\":\"08:00\"}",old,"wake-conflict");
  fact(id,"B","wake","{\"valueType\":\"TEXT\",\"value\":\"09:00\"}",null,"wake-conflict");
  var response=mvc.perform(get("/patients/"+id+"/health-summary").header("Authorization",TOKEN)).andExpect(status().isOk()).andExpect(jsonPath("$.data.profileRevision").value("1")).andExpect(jsonPath("$.data.facts.length()").value(2)).andExpect(jsonPath("$.data.axes.H.status").value("UNKNOWN")).andExpect(jsonPath("$.data.conflicts.length()").value(1)).andReturn().getResponse().getContentAsString();
  assertThat(response).doesNotContain(old);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM health_input_snapshot WHERE patient_id=?",Integer.class,id)).isZero();
 }
 @Test void rejectsRealOtherOrganizationAndRevokedAccess() throws Exception {
  String real=patient("demo-org","REAL","真实模式占位",true);
  String other=patient("other-org","DEMO","其他机构虚构患者",true);
  String revoked=patient("demo-org","DEMO","授权撤销虚构患者",true);
  db.update("UPDATE patient_access_grant SET revoked_at=UTC_TIMESTAMP(6) WHERE patient_id=?",revoked);
  mvc.perform(get("/patients/"+real+"/health-summary").header("Authorization",TOKEN)).andExpect(status().isNotFound());
  mvc.perform(get("/patients/"+other+"/health-summary").header("Authorization",TOKEN)).andExpect(status().isNotFound());
  mvc.perform(get("/patients/"+revoked+"/health-summary").header("Authorization",TOKEN)).andExpect(status().isForbidden());
 }
}
