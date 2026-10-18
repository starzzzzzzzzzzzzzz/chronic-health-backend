package com.healthsystem.config;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration
public class WorkflowClockConfiguration {
 @Bean Clock workflowClock(){return Clock.systemUTC();}
}
