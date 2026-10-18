package com.healthsystem.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import com.healthsystem.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class TaskSourcePolicy {
    public record Source(ReviewSubmission submission,ReviewDecision decision) {}
    private final ReviewSubmissionMapper submissions;private final ReviewDecisionMapper decisions;
    private final ConditionConfirmationServiceImpl confirmations;
    public TaskSourcePolicy(ReviewSubmissionMapper s,ReviewDecisionMapper d,ConditionConfirmationServiceImpl c){submissions=s;decisions=d;confirmations=c;}
    public Source approved(PlanReviewSupport.Context c) {
        if(!"APPROVED".equals(c.version().getStatus()) || !c.version().getId().equals(c.plan().getCurrentVersionId()))
            throw BusinessException.conflict("Current approved plan required");
        var s=submissions.selectOne(new QueryWrapper<ReviewSubmission>().eq("org_id",c.version().getOrgId()).eq("plan_version_id",c.version().getId()).eq("stage","PLAN").eq("status","APPROVED").orderByDesc("submitted_at").last("LIMIT 1"));
        if(s==null || s.getTargetRevision()!=c.version().getContentRevision().longValue())throw BusinessException.conflict("Approved source review required");
        String hash=CanonicalJson.hash(JsonValues.tree(Map.of("planContentHash",c.draft().contentHash(),"confirmations",confirmations.manifest(confirmations.view(c)))));
        if(!hash.equals(s.getContentHash()))throw BusinessException.conflict("Source review no longer matches plan");
        var d=decisions.selectOne(new QueryWrapper<ReviewDecision>().eq("org_id",s.getOrgId()).eq("submission_id",s.getId()).eq("decision","APPROVE"));
        if(d==null)throw BusinessException.conflict("Source approval decision required");
        return new Source(s,d);
    }
    public void validate(TaskBundle b,PlanReviewSupport.Context c) {
        var s=approved(c);
        if(!b.getPatientId().equals(c.plan().getPatientId()) || !b.getSourceReviewSubmissionId().equals(s.submission().getId())
                || !b.getSourceReviewDecisionId().equals(s.decision().getId()) || !b.getSourcePlanHash().equals(c.draft().contentHash())
                || !b.getSourceReviewHash().equals(s.submission().getContentHash()) || b.getSourceRevision()!=c.version().getContentRevision().longValue())
            throw BusinessException.conflict("Task bundle source changed");
    }
}
