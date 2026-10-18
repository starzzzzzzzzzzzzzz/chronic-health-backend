package com.healthsystem.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.entity.ReviewSubmission;
import com.healthsystem.mapper.ReviewSubmissionMapper;
import com.healthsystem.service.PlanReviewService;
import com.healthsystem.vo.ReviewSubmissionVO;
import com.healthsystem.exception.BusinessException;
import org.springframework.stereotype.Service;
@Service
public class ReviewWorkflowDispatcher {
    private final ReviewSubmissionMapper rows;private final PlanReviewService plans;private final TaskReviewService tasks;
    public ReviewWorkflowDispatcher(ReviewSubmissionMapper r,PlanReviewService p,TaskReviewService t){rows=r;plans=p;tasks=t;}
    private boolean task(BusinessPrincipal p,String id){var s=rows.selectOne(new QueryWrapper<ReviewSubmission>().eq("org_id",p.orgId()).eq("id",id));if(s==null)throw new BusinessException(404,"Review submission not found");return "TASK".equals(s.getStage());}
    public ReviewSubmissionVO get(BusinessPrincipal p,String id){return task(p,id)?tasks.get(p,id):plans.getSubmission(p,id);}
    public ReviewSubmissionVO decide(BusinessPrincipal p,String id,ReviewWorkflowRequest body){return task(p,id)?tasks.decide(p,id,body):plans.decide(p,id,body);}
    public ReviewSubmissionVO withdraw(BusinessPrincipal p,String id,ReviewWorkflowRequest body){return task(p,id)?tasks.withdraw(p,id,body):plans.withdraw(p,id,body);}
}
