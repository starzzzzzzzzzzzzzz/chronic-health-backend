package com.healthsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthsystem.entity.ReviewSubmission;
import org.apache.ibatis.annotations.Param;
public interface ReviewSubmissionMapper extends BaseMapper<ReviewSubmission> {
    ReviewSubmission lockSubmission(@Param("org") String org,@Param("id") String id);
}
