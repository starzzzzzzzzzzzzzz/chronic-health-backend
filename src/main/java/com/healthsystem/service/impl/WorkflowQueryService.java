package com.healthsystem.service.impl;

import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.exception.BusinessException;
import com.healthsystem.service.PatientService;
import com.healthsystem.vo.PageVO;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Read-side discovery. Authorization is applied before counting and pagination. */
@Service
@Transactional(readOnly=true)
public class WorkflowQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PlanReviewSupport support;
    private final PatientService patients;
    public WorkflowQueryService(NamedParameterJdbcTemplate jdbc, PlanReviewSupport support, PatientService patients) {
        this.jdbc=jdbc; this.support=support; this.patients=patients;
    }
    private static String grant(String ability) {
        return "EXISTS (SELECT 1 FROM patient_access_grant g WHERE g.org_id=p.org_id AND g.patient_id=p.id AND g.subject_id=:subject AND g.capability="+ability+" AND g.revoked_at IS NULL AND g.valid_from<=:now AND (g.valid_until IS NULL OR g.valid_until>:now))";
    }
    private static final String ACCESS="p.org_id=:org AND p.data_mode=:mode AND p.status='ACTIVE' AND "+grant("'PATIENT_READ'");
    private Map<String,Object> params(BusinessPrincipal p) {
        var m=new HashMap<String,Object>(); m.put("org",p.orgId());m.put("mode",p.dataMode());m.put("subject",p.subjectId());m.put("now",LocalDateTime.now(Clock.systemUTC()));return m;
    }
    private static void paging(int page,int size) {
        if(page<1||size<1||size>100)throw BusinessException.badRequest("分页参数无效：页码至少为1，每页数量为1至100");
    }
    private PageVO<Map<String,Object>> page(String fields,String from,String order,Map<String,Object> args,int page,int size) {
        paging(page,size);args.put("limit",size);args.put("offset",((long)page-1)*size);
        long count=jdbc.queryForObject("SELECT COUNT(*) "+from,args,Long.class);
        return new PageVO<>(page,size,count,jdbc.queryForList("SELECT "+fields+" "+from+" ORDER BY "+order+" LIMIT :limit OFFSET :offset",args));
    }
    public PageVO<Map<String,Object>> plans(BusinessPrincipal p,String patientId,String status,int page,int size) {
        var args=params(p);String from="FROM care_plan c JOIN patient p ON p.id=c.patient_id AND p.org_id=c.org_id JOIN care_plan_version v ON v.id=c.current_version_id AND v.org_id=c.org_id WHERE "+ACCESS;
        if(patientId!=null){args.put("patient",patientId);from+=" AND p.id=:patient";}
        if(status!=null){if(!Set.of("DRAFT","IN_REVIEW","APPROVED").contains(status))throw BusinessException.badRequest("方案状态无效");args.put("status",status);from+=" AND v.status=:status";}
        return page("c.id AS planId,p.id AS patientId,p.name AS patientName,v.id AS versionId,v.version_no AS versionNo,v.title,v.status,CAST(v.row_version AS CHAR) AS rowVersion,v.start_date AS startDate,v.end_date AS endDate",from,"v.updated_at DESC,v.id DESC",args,page,size);
    }
    public PageVO<Map<String,Object>> reviews(BusinessPrincipal p,String scope,String stage,int page,int size) {
        if(!Set.of("pending","mine").contains(scope))throw BusinessException.badRequest("审核查询范围无效");
        var args=params(p);
        String from="FROM review_submission r LEFT JOIN task_bundle b ON b.id=r.task_bundle_id AND b.org_id=r.org_id JOIN care_plan_version v ON v.id=COALESCE(r.plan_version_id,b.plan_version_id) AND v.org_id=r.org_id JOIN care_plan c ON c.id=v.plan_id AND c.org_id=v.org_id JOIN patient p ON p.id=c.patient_id AND p.org_id=c.org_id WHERE "+ACCESS;
        // TASK_READ is required by the detail endpoint, even for the submitting user.
        from+=" AND (r.stage='PLAN' OR "+grant("'TASK_READ'")+")";
        if(scope.equals("pending"))from+=" AND r.status='PENDING' AND r.submitted_by<>:subject AND "+grant("CASE WHEN r.stage='PLAN' THEN 'PLAN_REVIEW' ELSE 'TASK_REVIEW' END");
        else from+=" AND r.submitted_by=:subject";
        if(stage!=null){if(!Set.of("PLAN","TASK").contains(stage))throw BusinessException.badRequest("审核阶段无效");args.put("stage",stage);from+=" AND r.stage=:stage";}
        return page("r.id,r.stage,r.status,v.id AS planVersionId,r.task_bundle_id AS taskBundleId,r.schedule_preview_id AS schedulePreviewId,CAST(r.row_version AS CHAR) AS rowVersion,CAST(r.target_revision AS CHAR) AS targetRevision,p.id AS patientId,p.name AS patientName,v.title,r.submitted_by AS submittedBy",from,"r.submitted_at DESC,r.id DESC",args,page,size);
    }
    public Map<String,Object> workflow(BusinessPrincipal p,String versionId) {
        var c=support.context(p,versionId,"PATIENT_READ",false);
        // The aggregate includes task and publication metadata; match their existing read capability.
        patients.require(p,c.plan().getPatientId(),"TASK_READ",false);
        var args=params(p);args.put("plan",c.plan().getId());args.put("version",versionId);
        var result=new LinkedHashMap<String,Object>();result.put("planId",c.plan().getId());result.put("versionId",versionId);result.put("currentVersionId",c.plan().getCurrentVersionId());result.put("patientId",c.plan().getPatientId());
        result.put("versions",jdbc.queryForList("SELECT id AS versionId,version_no AS versionNo,status,title,CAST(row_version AS CHAR) AS rowVersion FROM care_plan_version WHERE org_id=:org AND plan_id=:plan ORDER BY version_no DESC,id DESC",args));
        result.put("taskBundles",jdbc.queryForList("SELECT id,status,CAST(row_version AS CHAR) AS rowVersion,CAST(content_revision AS CHAR) AS contentRevision FROM task_bundle WHERE org_id=:org AND plan_version_id=:version ORDER BY created_at DESC,id DESC",args));
        result.put("reviewSubmissions",jdbc.queryForList("SELECT r.id,r.stage,r.status,r.task_bundle_id AS taskBundleId,r.schedule_preview_id AS schedulePreviewId,CAST(r.row_version AS CHAR) AS rowVersion FROM review_submission r LEFT JOIN task_bundle b ON b.id=r.task_bundle_id AND b.org_id=r.org_id WHERE r.org_id=:org AND COALESCE(r.plan_version_id,b.plan_version_id)=:version ORDER BY r.submitted_at DESC,r.id DESC",args));
        result.put("publications",jdbc.queryForList("SELECT a.id,a.task_bundle_id AS taskBundleId,a.status,a.notification_status AS notificationStatus,a.review_submission_id AS reviewSubmissionId,a.schedule_preview_id AS schedulePreviewId,a.publication_preview_id AS publicationPreviewId FROM publication a JOIN task_bundle b ON b.id=a.task_bundle_id AND b.org_id=a.org_id WHERE a.org_id=:org AND b.plan_version_id=:version ORDER BY a.created_at DESC,a.id DESC",args));
        return result;
    }
}
