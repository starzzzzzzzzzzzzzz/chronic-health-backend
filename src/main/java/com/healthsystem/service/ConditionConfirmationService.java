package com.healthsystem.service;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.vo.ConditionConfirmationVO;
public interface ConditionConfirmationService {
    ConditionConfirmationVO get(BusinessPrincipal principal,String versionId);
    ConditionConfirmationVO save(BusinessPrincipal principal,String versionId,ReviewWorkflowRequest request);
}
