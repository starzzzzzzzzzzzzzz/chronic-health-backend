package com.healthsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.TaskBundle;
public interface TaskBundleMapper extends BaseMapper<TaskBundle> {
 @org.apache.ibatis.annotations.Select("SELECT * FROM task_bundle WHERE org_id=#{org} AND id=#{id} FOR UPDATE") TaskBundle lock(@org.apache.ibatis.annotations.Param("org") String org,@org.apache.ibatis.annotations.Param("id") String id);
}
