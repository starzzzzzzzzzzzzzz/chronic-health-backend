package com.healthsystem.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import com.healthsystem.vo.ReviewSubmissionVO;
import com.healthsystem.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanDraftValidation.check;
import static com.healthsystem.service.impl.PlanReviewSupport.*;

@Service
public class TaskReviewService {
    private final TaskBundleStore store;private final TaskSourcePolicy source;private final TaskScheduleService schedules;
    private final ReviewSubmissionMapper submissions;private final ReviewDecisionMapper decisions;
    public TaskReviewService(TaskBundleStore st,TaskSourcePolicy s,TaskScheduleService sc,ReviewSubmissionMapper sm,ReviewDecisionMapper dm){store=st;source=s;schedules=sc;submissions=sm;decisions=dm;}
    @Transactional(readOnly=true) public Map<String,Object> context(BusinessPrincipal p,String id) {
        var c=store.context(p,id,"TASK_READ",false);var b=c.bundle();
        var history=submissions.selectList(new QueryWrapper<ReviewSubmission>().eq("org_id",p.orgId()).eq("task_bundle_id",id).orderByAsc("submitted_at","id"));
        var data=new LinkedHashMap<String,Object>();data.put("bundle",store.view(b));data.put("schedulePreview",b.getLatestPreviewId()==null?null:schedules.get(p,b.getLatestPreviewId()));data.put("submissions",history.stream().map(this::view).toList());return data;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public ReviewSubmissionVO submit(BusinessPrincipal p,String id,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"expectedRevision","schedulePreviewId");long expected=expected(body);String previewId=string(body,"schedulePreviewId",true,36);
        var c=store.context(p,id,"TASK_EDIT",true);var b=c.bundle();store.requireRevision(b,expected,true);source.validate(b,c.source());
        var preview=schedules.requireCurrent(p,b,previewId);
        var s=stamp(new ReviewSubmission(),p);s.setUpdatedAt(s.getCreatedAt());s.setRowVersion(0L);s.setStage("TASK");s.setTaskBundleId(id);s.setSchedulePreviewId(previewId);s.setTargetRevision(b.getContentRevision());s.setContentHash(reviewHash(b,preview));
        s.setContentSnapshotJson(JsonValues.write(Map.of("schemaVersion","task-review-v1","dataMode",p.dataMode(),"bundle",store.view(b),"schedulePreview",TaskScheduleService.view(preview))));
        s.setStatus("PENDING");s.setSubmittedBy(p.subjectId());s.setSubmittedAt(s.getCreatedAt());submissions.insert(s);store.advance(p,b,"IN_REVIEW",false,"TASK_REVIEW_SUBMITTED");return view(s);
    }
    @Transactional(readOnly=true) public ReviewSubmissionVO get(BusinessPrincipal p,String id){var s=find(p,id);store.context(p,s.getTaskBundleId(),"TASK_READ",false);return view(s);}
    @Transactional(isolation=Isolation.READ_COMMITTED) public ReviewSubmissionVO decide(BusinessPrincipal p,String id,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"expectedRevision","decision","comment");long expected=expected(body);String decision=string(body,"decision",true,16);check(Set.of("APPROVE","RETURN").contains(decision),"Invalid review decision");String comment=string(body,"comment","RETURN".equals(decision),20000);
        var initial=find(p,id);var c=store.context(p,initial.getTaskBundleId(),"TASK_REVIEW",true);var s=submissions.lockSubmission(p.orgId(),id);
        if(p.subjectId().equals(s.getSubmittedBy()))throw new BusinessException(403,"Self-review is not allowed");
        pending(p,c.bundle(),s,expected);if("APPROVE".equals(decision))source.validate(c.bundle(),c.source());
        var d=stamp(new ReviewDecision(),p);d.setSubmissionId(id);d.setDecision(decision);d.setComment(comment);d.setReviewerId(p.subjectId());d.setReviewedAt(d.getCreatedAt());decisions.insert(d);
        finish(s,p,"APPROVE".equals(decision)?"APPROVED":"RETURNED",null);store.advance(p,c.bundle(),"APPROVE".equals(decision)?"APPROVED":"DRAFT",false,"TASK_REVIEW_DECIDED");return view(s);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public ReviewSubmissionVO withdraw(BusinessPrincipal p,String id,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"expectedRevision","reason");long expected=expected(body);String reason=string(body,"reason",true,1000);
        var initial=find(p,id);var c=store.context(p,initial.getTaskBundleId(),"TASK_EDIT",true);var s=submissions.lockSubmission(p.orgId(),id);
        if(!p.subjectId().equals(s.getSubmittedBy()))throw new BusinessException(403,"Only the submitter may withdraw");
        pending(p,c.bundle(),s,expected);finish(s,p,"WITHDRAWN",reason);store.advance(p,c.bundle(),"DRAFT",false,"TASK_REVIEW_WITHDRAWN");return view(s);
    }
    private void pending(BusinessPrincipal p,TaskBundle b,ReviewSubmission s,long expected) {
        if(s.getRowVersion()!=expected || !"PENDING".equals(s.getStatus()) || !"IN_REVIEW".equals(b.getStatus()) || s.getTargetRevision()!=b.getContentRevision().longValue())throw BusinessException.conflict("Task review is closed or stale");
        var preview=schedules.requireCurrent(p,b,s.getSchedulePreviewId());
        if(!reviewHash(b,preview).equals(s.getContentHash()))throw BusinessException.conflict("Task review content changed");
    }
    public static String reviewHash(TaskBundle b,SchedulePreview preview){return CanonicalJson.hash(JsonValues.tree(Map.of("bundleHash",b.getContentHash(),"previewHash",preview.getPreviewHash(),"sourceReviewHash",b.getSourceReviewHash())));}
    private void finish(ReviewSubmission s,BusinessPrincipal p,String status,String reason) {
        s.setStatus(status);s.setRowVersion(Math.addExact(s.getRowVersion(),1));s.setClosedAt(now());s.setUpdatedAt(s.getClosedAt());s.setClosedBy(p.subjectId());s.setClosedReason(reason);
        if(submissions.updateById(s)!=1)throw BusinessException.conflict("Task review changed");
    }
    private ReviewSubmission find(BusinessPrincipal p,String id) {
        var s=submissions.selectOne(new QueryWrapper<ReviewSubmission>().eq("org_id",p.orgId()).eq("id",id).eq("stage","TASK"));
        if(s==null)throw new BusinessException(404,"Task review not found");return s;
    }
    public ReviewSubmissionVO view(ReviewSubmission s) {
        var d=decisions.selectOne(new QueryWrapper<ReviewDecision>().eq("org_id",s.getOrgId()).eq("submission_id",s.getId()));
        var decision=d==null?null:new ReviewSubmissionVO.Decision(d.getId(),d.getDecision(),d.getComment(),d.getReviewerId(),utc(d.getReviewedAt()));
        return new ReviewSubmissionVO(s.getId(),null,s.getStage(),String.valueOf(s.getRowVersion()),String.valueOf(s.getTargetRevision()),s.getContentHash(),s.getStatus(),JsonValues.read(s.getContentSnapshotJson()),s.getSubmittedBy(),utc(s.getSubmittedAt()),s.getClosedBy(),utc(s.getClosedAt()),s.getClosedReason(),decision,s.getTaskBundleId(),s.getSchedulePreviewId());
    }
}
