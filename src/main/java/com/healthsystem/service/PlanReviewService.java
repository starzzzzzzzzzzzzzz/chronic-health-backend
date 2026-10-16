package com.healthsystem.service;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.vo.*;
public interface PlanReviewService {
    PlanReviewContextVO context(BusinessPrincipal p,String version);
    ReviewSubmissionVO submit(BusinessPrincipal p,String version,ReviewWorkflowRequest request);
    ReviewSubmissionVO getSubmission(BusinessPrincipal p,String submission);
    ReviewSubmissionVO decide(BusinessPrincipal p,String submission,ReviewWorkflowRequest request);
    ReviewSubmissionVO withdraw(BusinessPrincipal p,String submission,ReviewWorkflowRequest request);
}
