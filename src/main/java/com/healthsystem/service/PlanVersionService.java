package com.healthsystem.service;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.vo.CarePlanDraftVO;
public interface PlanVersionService {
    CarePlanDraftVO create(BusinessPrincipal p,String planId,ReviewWorkflowRequest request);
}
