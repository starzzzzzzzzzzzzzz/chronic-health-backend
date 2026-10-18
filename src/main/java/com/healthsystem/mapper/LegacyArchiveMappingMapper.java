package com.healthsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.LegacyArchiveMapping;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

public interface LegacyArchiveMappingMapper extends BaseMapper<LegacyArchiveMapping> {
    @Insert("""
            INSERT INTO legacy_archive_mapping (org_id, patient_id, created_at)
            VALUES (#{orgId}, #{patientId}, #{createdAt})
            ON DUPLICATE KEY UPDATE patient_id = patient_id
            """)
    int insertIfAbsent(@Param("orgId") String orgId, @Param("patientId") String patientId,
                       @Param("createdAt") LocalDateTime createdAt);
}
