package com.healthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.entity.LegacyArchiveMapping;
import com.healthsystem.entity.Patient;
import com.healthsystem.exception.BusinessException;
import com.healthsystem.mapper.LegacyArchiveMappingMapper;
import com.healthsystem.service.PatientService;
import org.springframework.stereotype.Service;

/** An archive alias never grants access by itself. */
@Service
public class LegacyArchiveResolver {
    private final LegacyArchiveMappingMapper mappings;
    private final PatientService patients;
    public LegacyArchiveResolver(LegacyArchiveMappingMapper mappings, PatientService patients) {
        this.mappings = mappings; this.patients = patients;
    }
    public Patient require(BusinessPrincipal principal, String archiveId, String capability, boolean lock) {
        long number;
        try {
            if (archiveId == null || !archiveId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            number = Long.parseLong(archiveId);
        } catch (NumberFormatException e) { throw BusinessException.badRequest("Invalid archiveId"); }
        var mapping = mappings.selectOne(new QueryWrapper<LegacyArchiveMapping>()
                .eq("archive_id", number).eq("org_id", principal.orgId()));
        if (mapping == null) throw new BusinessException(404, "Archive not found");
        return patients.require(principal, mapping.getPatientId(), capability, lock);
    }
}
