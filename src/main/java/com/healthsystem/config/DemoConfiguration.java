package com.healthsystem.config;

import com.healthsystem.service.impl.DemoDataInitializer;
import com.healthsystem.service.LegacyArchiveMappingService;
import com.healthsystem.mapper.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@Profile("local")
@ConditionalOnProperty(name="health.demo.enabled", havingValue="true")
public class DemoConfiguration {
    @Bean DemoDataInitializer demoDataInitializer(PatientMapper patients, HealthFactMapper facts, PatientAccessGrantMapper grants,AppMetadataMapper metadata, LegacyArchiveMappingService mappings) {
        return new DemoDataInitializer(patients, facts, grants, metadata, mappings);
    }
}
