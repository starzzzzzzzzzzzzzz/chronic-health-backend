package com.healthsystem;
import java.time.*;
public final class MutableWorkflowClock extends Clock {
 private volatile Instant value=Instant.parse("2026-10-16T00:00:00Z");
 public void set(String instant){value=Instant.parse(instant);}
 @Override public ZoneId getZone(){return ZoneOffset.UTC;}
 @Override public Clock withZone(ZoneId zone){return Clock.fixed(value,zone);}
 @Override public Instant instant(){return value;}
}
