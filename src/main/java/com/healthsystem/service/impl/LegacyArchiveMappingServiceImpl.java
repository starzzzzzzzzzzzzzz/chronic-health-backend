package com.healthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.entity.LegacyArchiveMapping;
import com.healthsystem.exception.BusinessException;
import com.healthsystem.mapper.LegacyArchiveMappingMapper;
import com.healthsystem.service.LegacyArchiveMappingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class LegacyArchiveMappingServiceImpl implements LegacyArchiveMappingService {
    private final LegacyArchiveMappingMapper mappings;
    private final Clock clock;

    public LegacyArchiveMappingServiceImpl(LegacyArchiveMappingMapper mappings, Clock clock) {
        this.mappings = mappings;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long ensureForPatient(String orgId, String patientId) {
        mappings.insertIfAbsent(orgId, patientId, LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        // A locking read sees the winner even if this transaction already has an older snapshot.
        var mapping = mappings.selectOne(new QueryWrapper<LegacyArchiveMapping>()
                .eq("org_id", orgId).eq("patient_id", patientId).last("FOR UPDATE"));
        if (mapping == null || mapping.getArchiveId() <= 0) {
            throw BusinessException.conflict("Legacy archive mapping is invalid");
        }
        return mapping.getArchiveId();
    }
}
