package com.healthsystem;
import com.healthsystem.entity.AuditLog;
import com.healthsystem.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class CarePlanDraftDatabaseTest extends DatabaseApiTestSupport {
 @MockitoSpyBean AuditLogMapper audit;
 @Test void auditWriteFailureRollsBackAlreadyWrittenDraftChildrenAndRevision() throws Exception {
  String patient=patient("demo-org","DEMO","失败回滚虚构患者",true);String version=null;
  String fact=fact(patient,"B","routine","{}",null,null);
  try {
   String body="{\"patientId\":\""+patient+"\",\"expectedProfileRevision\":\"1\",\"title\":\"原草稿\",\"startDate\":\"2026-10-17\",\"endDate\":\"2026-10-23\",\"zoneId\":\"Asia/Shanghai\"}";
   String response=mvc.perform(post("/care-plans").header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
   version=json.readTree(response).path("data").path("versionId").asText();
   doThrow(new DataIntegrityViolationException("simulated audit write failure")).when(audit).insert(any(AuditLog.class));
   String patch="{\"expectedRevision\":\"0\",\"title\":\"不应留下\",\"modules\":[{\"moduleCode\":\"B0\",\"selectionReason\":\"测试回滚\",\"factIds\":[\""+fact+"\"]}]}";
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/plan-versions/"+version).header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(patch)).andExpect(status().isConflict());
   assertThat(db.queryForObject("SELECT title FROM care_plan_version WHERE id=?",String.class,version)).isEqualTo("原草稿");
   assertThat(db.queryForObject("SELECT row_version FROM care_plan_version WHERE id=?",Long.class,version)).isZero();
   assertThat(db.queryForObject("SELECT COUNT(*) FROM care_plan_module WHERE plan_version_id=?",Integer.class,version)).isZero();
   assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE target_id=?",Integer.class,version)).isEqualTo(1);
  } finally {
   reset(audit);
   if(version!=null){db.update("UPDATE care_plan SET current_version_id=NULL WHERE patient_id=?",patient);db.update("DELETE FROM audit_log WHERE target_id=?",version);db.update("DELETE FROM care_plan_version WHERE id=?",version);}
   db.update("DELETE FROM care_plan WHERE patient_id=?",patient);db.update("DELETE FROM health_input_snapshot WHERE patient_id=?",patient);db.update("DELETE FROM health_fact WHERE patient_id=?",patient);db.update("DELETE FROM patient_access_grant WHERE patient_id=?",patient);db.update("DELETE FROM patient WHERE id=?",patient);
  }
 }
}
