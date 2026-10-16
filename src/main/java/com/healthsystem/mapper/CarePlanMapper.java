package com.healthsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.CarePlan;
import com.healthsystem.entity.CarePlanVersion;
import org.apache.ibatis.annotations.Param;
public interface CarePlanMapper extends BaseMapper<CarePlan> {
 CarePlanVersion lockVersion(@Param("org") String org,@Param("id") String id);
}
