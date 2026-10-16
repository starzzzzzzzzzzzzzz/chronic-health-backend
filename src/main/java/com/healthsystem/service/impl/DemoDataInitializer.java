package com.healthsystem.service.impl;

import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.UUID;

public class DemoDataInitializer implements ApplicationRunner {
    private final PatientMapper patients;
    private final HealthFactMapper facts;
    private final PatientAccessGrantMapper grants;
    public DemoDataInitializer(PatientMapper patients, HealthFactMapper facts, PatientAccessGrantMapper grants) {
        this.patients=patients; this.facts=facts; this.grants=grants;
    }
    @Override @Transactional public void run(ApplicationArguments args) throws Exception {
        var mapper=JsonMapper.builder().build();
        var fixture=mapper.readTree(new ClassPathResource("demo/patient.json").getInputStream());
        String id=fixture.path("id").asText();
        Patient existing=patients.selectById(id);
        if (existing!=null) {
            if (!"demo-org".equals(existing.getOrgId()) || !"DEMO".equals(existing.getDataMode())) throw new IllegalStateException("Demo patient ID conflicts with non-demo data");
            return; // Do not recreate revoked grants or overwrite clinician edits on restart.
        }
        LocalDateTime now=LocalDateTime.now(Clock.systemUTC());
        Patient patient=new Patient(); patient.setId(id);patient.setOrgId("demo-org");patient.setCreatedAt(now);patient.setCreatedBy("system:demo");patient.setUpdatedAt(now);patient.setRowVersion(0L);patient.setName(fixture.path("name").asText());patient.setSex("UNKNOWN");patient.setStatus("ACTIVE");patient.setDataMode("DEMO");patient.setProfileRevision(1L);patients.insert(patient);
        for(var f:fixture.path("facts")) {
            HealthFact fact=new HealthFact();fact.setId(UUID.randomUUID().toString());fact.setOrgId("demo-org");fact.setCreatedAt(now);fact.setCreatedBy("system:demo");fact.setPatientId(id);fact.setAxisCode(f.path("axis").asText());fact.setFactCode(f.path("code").asText());fact.setSourceType("DEMO");fact.setConfirmationStatus(f.path("value").isNull()?"UNKNOWN":"REPORTED");fact.setValueJson(mapper.writeValueAsString(mapper.createObjectNode().put("valueType","TEXT").set("value",f.path("value"))));fact.setSourceLocatorJson("{\"fixture\":\"demo/patient.json\",\"fictional\":true}");facts.insert(fact);
        }
        for(String ability:new String[]{"PATIENT_READ","PLAN_EDIT"}) {
            PatientAccessGrant grant=new PatientAccessGrant();grant.setId(UUID.randomUUID().toString());grant.setOrgId("demo-org");grant.setCreatedAt(now);grant.setCreatedBy("system:demo");grant.setUpdatedAt(now);grant.setRowVersion(0L);grant.setPatientId(id);grant.setSubjectId("demo-clinician");grant.setCapability(ability);grant.setValidFrom(now);grants.insert(grant);
        }
    }
}
