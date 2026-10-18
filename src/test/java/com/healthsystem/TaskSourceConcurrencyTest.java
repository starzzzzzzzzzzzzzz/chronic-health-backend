package com.healthsystem;
import com.healthsystem.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class TaskSourceConcurrencyTest extends PublicationApiTestSupport {
 @MockitoSpyBean PatientService patientAccess;
 @Test void sourcePointerIsRereadAfterWaitingForANewPlanVersionToCommit() throws Exception {
  var f=approved("routine",true);var pool=Executors.newSingleThreadExecutor();var reachedLock=new CountDownLatch(1);var pending=new AtomicReference<Future<Integer>>();var cloned=new AtomicReference<String>();
  try {
   doAnswer(call->{if("TASK_EDIT".equals(call.getArgument(2))&&Boolean.TRUE.equals(call.getArgument(3)))reachedLock.countDown();return call.callRealMethod();}).when(patientAccess).require(any(),eq(f.patient()),anyString(),eq(true));
   new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status->{
    try {
     var v=postJson("/care-plans/"+f.plan()+"/versions",TOKEN,Map.of("sourceVersionId",f.version(),"expectedRevision","4","changeReason","与任务生成并发创建新草稿"),201);cloned.set(v.path("versionId").asText());
     pending.set(pool.submit(()->mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/plan-versions/"+f.version()+"/task-bundles").header("Authorization",TOKEN).contentType("application/json").content("{\"expectedRevision\":\"4\"}")).andReturn().getResponse().getStatus()));
     assertThat(reachedLock.await(10,TimeUnit.SECONDS)).isTrue();
    } catch(Exception e){throw new RuntimeException(e);}
   });
   assertThat(pending.get().get(30,TimeUnit.SECONDS)).isEqualTo(409);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM task_bundle WHERE patient_id=?",Integer.class,f.patient())).isZero();
  } finally {
   reset(patientAccess);pool.shutdownNow();
   if(cloned.get()!=null){String version=cloned.get();db.update("DELETE b FROM plan_item_basis b JOIN care_plan_item i ON i.id=b.plan_item_id WHERE i.plan_version_id=?",version);for(String table:List.of("care_plan_item","care_plan_dimension","care_plan_module","plan_management_focus"))db.update("DELETE FROM "+table+" WHERE plan_version_id=?",version);db.update("DELETE FROM audit_log WHERE target_id=?",version);db.update("UPDATE care_plan SET current_version_id=? WHERE id=?",f.version(),f.plan());db.update("DELETE FROM care_plan_version WHERE id=?",version);}
   cleanupTaskPatient(f.patient());
  }
 }
}
