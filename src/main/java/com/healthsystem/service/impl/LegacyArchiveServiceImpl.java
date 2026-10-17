package com.healthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.entity.LegacyArchiveMapping;
import com.healthsystem.exception.BusinessException;
import com.healthsystem.mapper.LegacyArchiveMappingMapper;
import com.healthsystem.service.LegacyArchiveService;
import com.healthsystem.service.PatientService;
import com.healthsystem.vo.LegacyArchivePageVO;
import com.healthsystem.vo.LegacyManagedArchiveVO;
import com.healthsystem.vo.PatientSummaryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LegacyArchiveServiceImpl implements LegacyArchiveService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final PatientService patients;
    private final LegacyArchiveMappingMapper mappings;
    private final Clock clock;

    public LegacyArchiveServiceImpl(PatientService patients, LegacyArchiveMappingMapper mappings, Clock clock) {
        this.patients = patients;
        this.mappings = mappings;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyArchivePageVO list(BusinessPrincipal principal, int page, int pageSize, String keyword) {
        var result = patients.list(principal, page, pageSize, keyword);
        if (result.items().isEmpty()) {
            return new LegacyArchivePageVO(List.of(), result.total(), result.page(), result.size());
        }
        var aliases = mappings.selectList(new QueryWrapper<LegacyArchiveMapping>()
                        .eq("org_id", principal.orgId())
                        .in("patient_id", result.items().stream().map(PatientSummaryVO::id).toList()))
                .stream().collect(Collectors.toMap(LegacyArchiveMapping::getPatientId, LegacyArchiveMapping::getArchiveId));
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        var items = result.items().stream().map(patient -> {
            Long archiveId = aliases.get(patient.id());
            if (archiveId == null || archiveId <= 0) {
                throw BusinessException.conflict("Legacy archive mapping is not ready");
            }
            LocalDate birthDate = patient.birthDate();
            Integer age = birthDate == null || birthDate.isAfter(today) ? null : Period.between(birthDate, today).getYears();
            return new LegacyManagedArchiveVO(archiveId.toString(), patient.name(), "", age);
        }).toList();
        return new LegacyArchivePageVO(items, result.total(), result.page(), result.size());
    }
}
