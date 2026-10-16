package com.healthsystem.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.entity.*;
import com.healthsystem.vo.*;
import com.healthsystem.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanReviewSupport.*;
@Service
public class TaskScheduleService {
    private final TaskBundleStore store;private final TaskSourcePolicy source;private final TaskSchedulePolicy policy;
    public TaskScheduleService(TaskBundleStore s,TaskSourcePolicy p,TaskSchedulePolicy e){store=s;source=p;policy=e;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public TaskSchedulePreviewVO preview(BusinessPrincipal p,String id,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"expectedRevision");long expected=expected(body);
        var c=store.context(p,id,"TASK_EDIT",true);var b=c.bundle();store.requireRevision(b,expected,true);source.validate(b,c.source());
        var groups=store.groups(b);var expanded=policy.expand(b,groups);var issues=new ArrayList<>(store.issues(b,groups));issues.addAll(expanded.issues());
        if(expanded.occurrences().isEmpty() && issues.isEmpty())issues.add(TaskBundleStore.issue("NO_OCCURRENCES","groups","No execution day matches this cycle"));
        var row=stamp(new SchedulePreview(),p);row.setTaskBundleId(id);row.setBundleRevision(b.getContentRevision());row.setContentHash(b.getContentHash());row.setRuleVersion(TaskTemplateCatalog.RULE_VERSION);
        row.setOccurrencesJson(JsonValues.write(expanded.occurrences()));row.setIssuesJson(JsonValues.write(issues));
        row.setPreviewHash(hash(row));store.previews.insert(row);b.setLatestPreviewId(row.getId());store.advance(p,b,null,false,"TASK_SCHEDULE_PREVIEWED");return view(row);
    }
    @Transactional(readOnly=true) public TaskSchedulePreviewVO get(BusinessPrincipal p,String id) {
        var row=find(p,id);store.context(p,row.getTaskBundleId(),"TASK_READ",false);return view(row);
    }
    public SchedulePreview find(BusinessPrincipal p,String id) {
        var row=store.previews.selectOne(new QueryWrapper<SchedulePreview>().eq("org_id",p.orgId()).eq("id",id));
        if(row==null)throw new BusinessException(404,"Schedule preview not found");return row;
    }
    public SchedulePreview requireCurrent(BusinessPrincipal p,TaskBundle b,String id) {
        var row=find(p,id);
        if(!row.getTaskBundleId().equals(b.getId()) || !id.equals(b.getLatestPreviewId()) || row.getBundleRevision()!=b.getContentRevision().longValue()
                || !row.getContentHash().equals(b.getContentHash()) || !store.hash(b).equals(b.getContentHash()) || !hash(row).equals(row.getPreviewHash()))throw BusinessException.conflict("Schedule preview is stale or does not match bundle");
        if(!JsonValues.read(row.getIssuesJson()).isEmpty() || !store.issues(b,store.groups(b)).isEmpty())throw BusinessException.badRequest("Task preview has blocking issues");
        return row;
    }
    public static String hash(SchedulePreview row){return CanonicalJson.hash(JsonValues.tree(Map.of("bundleRevision",String.valueOf(row.getBundleRevision()),"contentHash",row.getContentHash(),"ruleVersion",row.getRuleVersion(),"occurrences",JsonValues.read(row.getOccurrencesJson()),"issues",JsonValues.read(row.getIssuesJson()))));}
    public static TaskSchedulePreviewVO view(SchedulePreview r) {
        var issues=new ArrayList<PlanReviewContextVO.Issue>();for(var n:JsonValues.read(r.getIssuesJson()))issues.add(new PlanReviewContextVO.Issue(n.path("code").asText(),n.path("severity").asText(),n.path("fieldPath").asText(),n.path("message").asText()));
        return new TaskSchedulePreviewVO(r.getId(),r.getTaskBundleId(),String.valueOf(r.getBundleRevision()),r.getContentHash(),r.getPreviewHash(),r.getRuleVersion(),JsonValues.read(r.getOccurrencesJson()),issues);
    }
}
