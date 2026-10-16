package com.healthsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.HealthFact;
import org.apache.ibatis.annotations.Param;
import java.util.List;
public interface HealthFactMapper extends BaseMapper<HealthFact> {
    List<HealthFact> currentFacts(@Param("org") String org,@Param("patient") String patient);
}
