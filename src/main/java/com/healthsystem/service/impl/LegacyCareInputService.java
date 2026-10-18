package com.healthsystem.service.impl;

import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.service.PatientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class LegacyCareInputService {
    private final LegacyArchiveResolver archives;
    private final PatientService patients;
    public LegacyCareInputService(LegacyArchiveResolver archives, PatientService patients) {
        this.archives = archives; this.patients = patients;
    }
    @Transactional(readOnly = true)
    public Map<String, Object> input(BusinessPrincipal principal, String archiveId) {
        var patient = archives.require(principal, archiveId, "PATIENT_READ", false);
        var summary = patients.healthSummary(principal, patient.getId());
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("archiveId", archiveId); scope.put("shopId", principal.orgId());
        if (patient.getTeamId() != null) scope.put("teamId", patient.getTeamId());
        Map<String, Object> patientView = new LinkedHashMap<>();
        patientView.put("archiveId", archiveId); patientView.put("name", patient.getName());
        patientView.put("sex", patient.getSex()); patientView.put("dataMode", patient.getDataMode());
        if (patient.getBirthDate() != null) patientView.put("birthDate", patient.getBirthDate());
        var facts = summary.facts().stream().map(f -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("factId", f.id()); row.put("axisCode", f.axisCode()); row.put("factCode", f.factCode());
            row.put("value", f.value()); row.put("unit", f.unit()); row.put("sourceType", f.sourceType());
            row.put("sourceRef", f.sourceRef()); row.put("status", f.confirmationStatus());
            row.put("observedAt", f.observedAt()); row.put("confidence", f.confidence());
            row.put("confirmedAt", f.confirmedAt()); row.put("confirmedBy", f.confirmedBy());
            return row;
        }).toList();
        return Map.of("scope", scope, "patient", patientView, "profileRevision", summary.profileRevision(),
                "axes", summary.axes(), "facts", facts, "conflicts", summary.conflicts(), "missingFields", summary.missingFields());
    }
}
