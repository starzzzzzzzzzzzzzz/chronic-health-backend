package com.healthsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.ExecutionTask;
public interface ExecutionTaskMapper extends BaseMapper<ExecutionTask> {
 @org.apache.ibatis.annotations.Select("SELECT * FROM execution_task WHERE org_id=#{org} AND id=#{id} FOR UPDATE") ExecutionTask lock(@org.apache.ibatis.annotations.Param("org") String org,@org.apache.ibatis.annotations.Param("id") String id);
}
