package com.healthsystem.service;

public interface LegacyArchiveMappingService {
    /** Called inside the transaction that writes the patient; never from a GET. */
    Long ensureForPatient(String orgId, String patientId);
}
