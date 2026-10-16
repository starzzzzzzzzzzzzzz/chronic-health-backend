package com.healthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.entity.*;
import com.healthsystem.exception.BusinessException;
import com.healthsystem.mapper.*;
import com.healthsystem.service.PlanReviewService;
import com.healthsystem.vo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanDraftValidation.*;
import static com.healthsystem.service.impl.PlanReviewSupport.*;

@Service
public class PlanReviewServiceImpl implements PlanReviewService {
    private final PlanReviewSupport support;
    private final ConditionConfirmationServiceImpl conditions;
    private final PlanReviewValidation validation;
    private final ReviewSubmissionMapper submissions;
    private final ReviewDecisionMapper decisions;
    public PlanReviewServiceImpl(PlanReviewSupport support,ConditionConfirmationServiceImpl conditions,PlanReviewValidation validation,ReviewSubmissionMapper submissions,ReviewDecisionMapper decisions) {
        this.support=support;this.conditions=conditions;this.validation=validation;this.submissions=submissions;this.decisions=decisions;
    }
    @Override @Transactional(readOnly=true)
    public PlanReviewContextVO context(BusinessPrincipal p,String id) {
        var c=support.context(p,id,"PATIENT_READ",false);var confirms=conditions.view(c);
        var history=submissions.selectList(new QueryWrapper<ReviewSubmission>().eq("org_id",p.orgId()).eq("plan_version_id",id).orderByAsc("submitted_at","id"));
        return new PlanReviewContextVO(c.draft(),confirms,validation.issues(c.draft(),confirms),history.stream().map(this::view).toList());
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReviewSubmissionVO submit(BusinessPrincipal p,String id,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"expectedRevision");long expected=expected(body);
        var c=support.context(p,id,"PLAN_EDIT",true);support.editable(c,expected);
        var confirms=conditions.view(c);
        check(validation.issues(c.draft(),confirms).isEmpty(),"Plan has blocking issues; load review-context");
        var manifest=conditions.manifest(confirms);
        var snapshot=JsonValues.tree(Map.of("schemaVersion","plan-review-v1","dataMode",p.dataMode(),"plan",c.draft(),"confirmations",manifest,"validationPolicy","plan-review-v1"));
        var s=stamp(new ReviewSubmission(),p);s.setUpdatedAt(s.getCreatedAt());s.setRowVersion(0L);s.setStage("PLAN");s.setPlanVersionId(id);s.setTargetRevision(c.version().getContentRevision());
        s.setContentHash(reviewHash(c.draft(),confirms));s.setContentSnapshotJson(JsonValues.write(snapshot));s.setStatus("PENDING");s.setSubmittedBy(p.subjectId());s.setSubmittedAt(s.getCreatedAt());
        submissions.insert(s);support.advance(p,c,"IN_REVIEW","PLAN_REVIEW_SUBMITTED",Map.of("submissionId",s.getId(),"targetRevision",s.getTargetRevision(),"reviewHash",s.getContentHash()));
        return view(s);
    }
    @Override @Transactional(readOnly=true)
    public ReviewSubmissionVO getSubmission(BusinessPrincipal p,String id) {
        var s=find(p,id);support.context(p,s.getPlanVersionId(),"PATIENT_READ",false);return view(s);
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReviewSubmissionVO decide(BusinessPrincipal p,String id,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"expectedRevision","decision","comment");long expected=expected(body);
        String decision=string(body,"decision",true,16);check(Set.of("APPROVE","RETURN").contains(decision),"Invalid review decision");
        String comment=string(body,"comment","RETURN".equals(decision),20000);
        var initial=find(p,id);var c=support.context(p,initial.getPlanVersionId(),"PLAN_REVIEW",true);
        var s=submissions.lockSubmission(p.orgId(),id);
        if(p.subjectId().equals(s.getSubmittedBy()))throw new BusinessException(403,"Self-review is not allowed");
        pending(c,s,expected);
        var d=stamp(new ReviewDecision(),p);d.setSubmissionId(id);d.setDecision(decision);d.setComment(comment);d.setReviewerId(p.subjectId());d.setReviewedAt(d.getCreatedAt());decisions.insert(d);
        finish(s,p,"APPROVE".equals(decision)?"APPROVED":"RETURNED",null);
        support.advance(p,c,"APPROVE".equals(decision)?"APPROVED":"DRAFT","PLAN_REVIEW_DECIDED",Map.of("submissionId",id,"decisionId",d.getId(),"decision",decision,"reviewHash",s.getContentHash()));
        return view(s);
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReviewSubmissionVO withdraw(BusinessPrincipal p,String id,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"expectedRevision","reason");long expected=expected(body);String reason=string(body,"reason",true,1000);
        var initial=find(p,id);var c=support.context(p,initial.getPlanVersionId(),"PLAN_EDIT",true);var s=submissions.lockSubmission(p.orgId(),id);
        if(!p.subjectId().equals(s.getSubmittedBy()))throw new BusinessException(403,"Only the submitter may withdraw this review");
        pending(c,s,expected);finish(s,p,"WITHDRAWN",reason);
        support.advance(p,c,"DRAFT","PLAN_REVIEW_WITHDRAWN",Map.of("submissionId",id));return view(s);
    }
    private void pending(Context c,ReviewSubmission s,long expected) {
        if(s.getRowVersion()!=expected || !"PENDING".equals(s.getStatus()) || !"IN_REVIEW".equals(c.version().getStatus()))throw BusinessException.conflict("Review is closed or has changed");
        var confirms=conditions.view(c);
        if(s.getTargetRevision()!=c.version().getContentRevision().longValue() || !s.getContentHash().equals(reviewHash(c.draft(),confirms)))throw BusinessException.conflict("Review target no longer matches");
    }
    private String reviewHash(CarePlanDraftVO draft,ConditionConfirmationVO confirms) {
        return CanonicalJson.hash(JsonValues.tree(Map.of("planContentHash",draft.contentHash(),"confirmations",conditions.manifest(confirms))));
    }
    private void finish(ReviewSubmission s,BusinessPrincipal p,String status,String reason) {
        s.setStatus(status);s.setRowVersion(Math.addExact(s.getRowVersion(),1));s.setClosedAt(now());s.setUpdatedAt(s.getClosedAt());s.setClosedBy(p.subjectId());s.setClosedReason(reason);
        if(submissions.updateById(s)!=1)throw BusinessException.conflict("Review changed");
    }
    private ReviewSubmission find(BusinessPrincipal p,String id) {
        var s=submissions.selectOne(new QueryWrapper<ReviewSubmission>().eq("org_id",p.orgId()).eq("id",id));if(s==null)throw new BusinessException(404,"Review submission not found");return s;
    }
    private ReviewSubmissionVO view(ReviewSubmission s) {
        var d=decisions.selectOne(new QueryWrapper<ReviewDecision>().eq("org_id",s.getOrgId()).eq("submission_id",s.getId()));
        var decision=d==null?null:new ReviewSubmissionVO.Decision(d.getId(),d.getDecision(),d.getComment(),d.getReviewerId(),utc(d.getReviewedAt()));
        return new ReviewSubmissionVO(s.getId(),s.getPlanVersionId(),s.getStage(),String.valueOf(s.getRowVersion()),String.valueOf(s.getTargetRevision()),s.getContentHash(),s.getStatus(),JsonValues.read(s.getContentSnapshotJson()),s.getSubmittedBy(),utc(s.getSubmittedAt()),s.getClosedBy(),utc(s.getClosedAt()),s.getClosedReason(),decision);
    }
}
