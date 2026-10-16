package com.healthsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.Patient;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
public interface PatientMapper extends BaseMapper<Patient> {
    Patient findScoped(@Param("id") String id,@Param("org") String org,@Param("mode") String mode,@Param("lock") boolean lock);
    long countAccessible(@Param("org") String org,@Param("subject") String subject,@Param("mode") String mode,@Param("query") String query,@Param("now") LocalDateTime now);
    List<Patient> listAccessible(@Param("org") String org,@Param("subject") String subject,@Param("mode") String mode,@Param("query") String query,@Param("now") LocalDateTime now,@Param("offset") long offset,@Param("limit") int limit);
}
