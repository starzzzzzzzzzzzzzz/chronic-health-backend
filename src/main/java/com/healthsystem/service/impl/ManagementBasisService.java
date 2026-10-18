package com.healthsystem.service.impl;

import com.healthsystem.common.json.JsonValues;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.EvaluateManagementRequest;
import com.healthsystem.exception.BusinessException;
import com.healthsystem.service.PatientService;
import com.healthsystem.vo.ManagementBasisVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagementBasisService {
    private final LegacyArchiveResolver archives;
    private final PatientService patients;
    private final ManagementFocusPolicy policy;
    public ManagementBasisService(LegacyArchiveResolver archives,PatientService patients,ManagementFocusPolicy policy) {
        this.archives=archives;this.patients=patients;this.policy=policy;
    }
    @Transactional(readOnly=true)
    public ManagementBasisVO legacy(BusinessPrincipal p,EvaluateManagementRequest r) {
        var patient=archives.require(p,r.archiveId(),"PATIENT_READ",false);
        if(r.shopId()!=null&&!r.shopId().equals(p.orgId())||r.teamId()!=null&&!r.teamId().equals(patient.getTeamId()))
            throw new BusinessException(403,"Requested shop/team does not match the authorized local scope");
        return policy.evaluate(p.orgId(),r.archiveId(),JsonValues.tree(patients.healthSummary(p,patient.getId())));
    }
    @Transactional(readOnly=true)
    public ManagementBasisVO nativeBasis(BusinessPrincipal p,String patientId) {
        return policy.evaluate(p.orgId(),patientId,JsonValues.tree(patients.healthSummary(p,patientId)));
    }
}
