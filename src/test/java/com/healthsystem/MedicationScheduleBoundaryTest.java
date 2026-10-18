package com.healthsystem.service.impl;
import com.healthsystem.common.json.JsonValues;
import com.healthsystem.entity.TaskBundle;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class MedicationScheduleBoundaryTest {
 TaskBundle bundle(){var b=new TaskBundle();b.setId("bundle");b.setPlanVersionId("plan");b.setSourceReviewSubmissionId("review");b.setSourceReviewDecisionId("decision");b.setSourcePlanHash("0".repeat(64));b.setStartDate(LocalDate.parse("2026-10-17"));b.setEndDate(b.getStartDate());b.setZoneId("Asia/Shanghai");return b;}
 JsonNode medication(String group,String time,String drug) {
  var rule=Map.of("isoWeekdays",List.of(6),"slots",List.of(Map.of("slotKey","dose","mode","FIXED","localTime",time,"opensOffsetMinutes",0,"closesOffsetMinutes",60)),"rotationPolicy","FIXED");
  return JsonValues.tree(Map.of("id",group,"dimensionCode","medication","professionalBinding",Map.of("drugName",drug,"schedule",rule),"variants",List.of(Map.of("id",group+"-variant")),"schedule",rule));
 }
 @Test void sameConfirmedDrugCapsMakeupAcrossDifferentSourceGroups() {
  var expansion=new TaskSchedulePolicy().expand(bundle(),List.of(medication("one","08:00","虚构同药"),medication("two","12:00","虚构同药")));
  assertThat(expansion.issues()).isEmpty();assertThat(expansion.occurrences().get(0).path("makeupCutoffAt").asText()).isEqualTo("2026-10-17T04:00:00Z");
 }
 @Test void unrelatedConfirmedDrugsDoNotShareTheirMakeupCap() {
  var expansion=new TaskSchedulePolicy().expand(bundle(),List.of(medication("one","08:00","虚构药一"),medication("two","12:00","虚构药二")));
  assertThat(expansion.occurrences().get(0).path("makeupCutoffAt").asText()).isEqualTo("2026-10-17T13:00:00Z");
 }
}
