package com.healthsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.AppMetadata;
import org.apache.ibatis.annotations.Param;

public interface AppMetadataMapper extends BaseMapper<AppMetadata> {
    String findValueByKey(@Param("key") String key);
}
