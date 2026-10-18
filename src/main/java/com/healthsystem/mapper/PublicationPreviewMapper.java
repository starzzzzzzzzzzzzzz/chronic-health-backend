package com.healthsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.PublicationPreview;
public interface PublicationPreviewMapper extends BaseMapper<PublicationPreview> {
 @org.apache.ibatis.annotations.Select("SELECT * FROM publication_preview WHERE org_id=#{org} AND id=#{id} FOR UPDATE") PublicationPreview lock(@org.apache.ibatis.annotations.Param("org") String org,@org.apache.ibatis.annotations.Param("id") String id);
}
