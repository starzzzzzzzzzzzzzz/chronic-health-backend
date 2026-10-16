package com.healthsystem.service.impl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
class TaskFeedbackPolicyTest {
 @ParameterizedTest @CsvSource({"medication,2026-10-17T13:00:00Z","diet,2026-10-18T16:00:00Z","exercise,2026-10-18T16:00:00Z","routine,2026-10-18T10:00:00Z","monitoring,2026-10-17T13:00:00Z","tcm,2026-10-17T07:00:00Z","other,2026-10-18T16:00:00Z"})
 void sevenDimensionsKeepTheirOwnMakeupDeadline(String dimension,String expected){assertThat(TaskSchedulePolicy.makeupDeadline(dimension,LocalDate.parse("2026-10-17"),Instant.parse("2026-10-17T01:00:00Z"),Instant.parse("2026-10-17T01:00:00Z"),ZoneId.of("Asia/Shanghai"))).isEqualTo(Instant.parse(expected));}
}
