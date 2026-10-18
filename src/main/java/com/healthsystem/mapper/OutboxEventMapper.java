package com.healthsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.OutboxEvent;
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {
 @org.apache.ibatis.annotations.Select("SELECT * FROM outbox_event WHERE org_id=#{org} AND id=#{id} FOR UPDATE") OutboxEvent lock(@org.apache.ibatis.annotations.Param("org") String org,@org.apache.ibatis.annotations.Param("id") String id);
}
