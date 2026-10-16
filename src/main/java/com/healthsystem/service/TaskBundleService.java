package com.healthsystem.service;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.vo.TaskBundleVO;
public interface TaskBundleService {
    TaskBundleVO generate(BusinessPrincipal p,String sourceId,ReviewWorkflowRequest body);
    TaskBundleVO get(BusinessPrincipal p,String id);
    TaskBundleVO update(BusinessPrincipal p,String id,ReviewWorkflowRequest body);
}
