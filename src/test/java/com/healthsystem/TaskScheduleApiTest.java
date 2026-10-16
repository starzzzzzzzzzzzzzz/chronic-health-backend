package com.healthsystem;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Transactional
class TaskScheduleApiTest extends TaskApiTestSupport {
    @Test void sevenDaysWithTwoSlotsProduceFourteenDistinctFrozenOccurrences() throws Exception {
        var f=approved("routine",true);var b=configure(bundle(f,"4"),List.of(slot("morning","08:00",0,60),slot("evening","20:00",0,60)));
        var p=preview(b);assertThat(p.path("occurrences").size()).isEqualTo(14);assertThat(p.path("issues").isEmpty()).isTrue();
        assertThat(p.path("occurrences").get(0).path("plannedAt").asText()).isEqualTo("2026-10-17T00:00:00Z");
        var keys=new HashSet<String>();for(var o:p.path("occurrences"))keys.add(o.path("occurrenceKey").asText());assertThat(keys).hasSize(14);
        assertThat(getJson("/schedule-previews/"+p.path("id").asText(),TOKEN,200).path("previewHash")).isEqualTo(p.path("previewHash"));
        var current=getJson("/task-bundles/"+b.path("id").asText(),TOKEN,200);
        var repeat=preview(current);assertThat(repeat.path("previewHash")).isEqualTo(p.path("previewHash"));
    }
    @Test void fourteenAndTwentyEightDayCyclesAreNotHardcodedToAWeek() throws Exception {
        for(String[] c:new String[][]{{"2026-10-30","14"},{"2026-11-13","28"}}) {
            var f=approvedPeriod("routine","2026-10-17",c[0],"Asia/Shanghai");var b=configure(generateCurrent(f),List.of(slot("daily","08:00",0,60)));
            assertThat(preview(b).path("occurrences").size()).isEqualTo(Integer.parseInt(c[1]));
        }
    }
    @Test void crossingMidnightKeepsExecutionDateAndComputesTheUtcWindow() throws Exception {
        var f=approved("routine",true);var p=preview(configure(bundle(f,"4"),List.of(slot("night","23:30",0,120))));
        var o=p.path("occurrences").get(0);
        assertThat(o.path("executionDate").asText()).isEqualTo("2026-10-17");
        assertThat(o.path("opensAt").asText()).isEqualTo("2026-10-17T15:30:00Z");
        assertThat(o.path("closesAt").asText()).isEqualTo("2026-10-17T17:30:00Z");
    }
    @Test void anchorModeDoesNotInventAnActualMealEvent() throws Exception {
        var b=readyBundle();var n=(tools.jackson.databind.node.ObjectNode)b.path("groups").get(0).path("schedule").deepCopy();
        ((tools.jackson.databind.node.ObjectNode)n.path("slots").get(0)).put("mode","ANCHOR");
        b=editBundle(b.path("id").asText(),Map.of("expectedRevision","1","groups",List.of(Map.of("id",b.path("groups").get(0).path("id").asText(),"schedule",n))),200);
        var p=preview(b);assertThat(p.path("issues").toString()).contains("ANCHOR_NOT_SUPPORTED");assertThat(p.path("occurrences").isEmpty()).isTrue();
    }
}
