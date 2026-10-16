package com.healthsystem.service;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.entity.Patient;
import com.healthsystem.vo.*;
public interface PatientService {
    PageVO<PatientSummaryVO> list(BusinessPrincipal principal,int page,int size,String query);
    PatientHealthSummaryVO healthSummary(BusinessPrincipal principal,String patientId);
    Patient require(BusinessPrincipal principal,String patientId,String capability,boolean lock);
}
