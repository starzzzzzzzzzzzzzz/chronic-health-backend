package com.healthsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.PublicationRequest;
public interface PublicationRequestMapper extends BaseMapper<PublicationRequest> {
 @org.apache.ibatis.annotations.Select("SELECT * FROM publication_request WHERE org_id=#{org} AND id=#{id} FOR UPDATE") PublicationRequest lock(@org.apache.ibatis.annotations.Param("org") String org,@org.apache.ibatis.annotations.Param("id") String id);
}
