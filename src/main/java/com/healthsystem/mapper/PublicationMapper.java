package com.healthsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.Publication;
public interface PublicationMapper extends BaseMapper<Publication> {
 @org.apache.ibatis.annotations.Select("SELECT * FROM publication WHERE org_id=#{org} AND id=#{id} FOR UPDATE") Publication lock(@org.apache.ibatis.annotations.Param("org") String org,@org.apache.ibatis.annotations.Param("id") String id);
}
