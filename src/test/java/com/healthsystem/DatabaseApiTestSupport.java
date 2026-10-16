package com.healthsystem;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;

@SpringBootTest(properties={"health.demo.enabled=true","health.demo.token=development-test-token-only"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
abstract class DatabaseApiTestSupport {
 static final String TOKEN="development-test-token-only";
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate db;
 final JsonMapper json=JsonMapper.builder().build();
 String patient(String org,String mode,String name,boolean grant) {
  String id=UUID.randomUUID().toString();
  db.update("INSERT INTO patient(id,org_id,created_at,created_by,updated_at,row_version,name,sex,status,data_mode,profile_revision) VALUES(?,?,UTC_TIMESTAMP(6),'test',UTC_TIMESTAMP(6),0,?,'UNKNOWN','ACTIVE',?,1)",id,org,name,mode);
  if(grant) for(String cap:new String[]{"PATIENT_READ","PLAN_EDIT"}) db.update("INSERT INTO patient_access_grant(id,org_id,created_at,created_by,updated_at,row_version,patient_id,subject_id,capability,valid_from) VALUES(?,?,UTC_TIMESTAMP(6),'test',UTC_TIMESTAMP(6),0,?,'demo-clinician',?,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),org,id,cap);
  return id;
 }
 String fact(String patient,String axis,String code,String value,String previous,String conflict) {
  String id=UUID.randomUUID().toString();
  db.update("INSERT INTO health_fact(id,org_id,created_at,created_by,patient_id,axis_code,fact_code,value_json,source_type,confirmation_status,supersedes_fact_id,conflict_group_key) VALUES(?,'demo-org',UTC_TIMESTAMP(6),'test',?,?,?,?,'DEMO','REPORTED',?,?)",id,patient,axis,code,value,previous,conflict);return id;
 }
}
