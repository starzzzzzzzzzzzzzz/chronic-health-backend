package com.healthsystem.service;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.*;
import com.healthsystem.vo.CarePlanDraftVO;
public interface CarePlanService {
 CarePlanDraftVO create(BusinessPrincipal principal,CreateCarePlanRequest request);
 CarePlanDraftVO get(BusinessPrincipal principal,String versionId);
 CarePlanDraftVO update(BusinessPrincipal principal,String versionId,UpdateCarePlanRequest request);
}
