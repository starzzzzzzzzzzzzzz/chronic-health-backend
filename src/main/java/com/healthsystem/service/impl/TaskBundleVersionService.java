package com.healthsystem.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.entity.*;
import com.healthsystem.vo.TaskBundleVO;
import com.healthsystem.exception.BusinessException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanReviewSupport.*;

@Service
public class TaskBundleVersionService {
    private final TaskBundleStore store;private final TaskSourcePolicy source;
    public TaskBundleVersionService(TaskBundleStore s,TaskSourcePolicy p){store=s;source=p;}
    @Transactional(isolation=Isolation.READ_COMMITTED) public TaskBundleVO create(BusinessPrincipal p,String id,ReviewWorkflowRequest request) {
        var body=request.body();fields(body,"expectedRevision","changeReason");long expected=expected(body);String reason=string(body,"changeReason",true,1000);
        var c=store.context(p,id,"TASK_EDIT",true);var old=c.bundle();store.requireRevision(old,expected,false);source.validate(old,c.source());
        int latest=store.bundles.selectList(new QueryWrapper<TaskBundle>().eq("org_id",p.orgId()).eq("plan_version_id",old.getPlanVersionId())).stream().mapToInt(TaskBundle::getVersionNo).max().orElse(0);
        if(!java.util.Set.of("APPROVED","PUBLISHED").contains(old.getStatus()) || old.getVersionNo()!=latest)throw BusinessException.conflict("Clone the latest approved task bundle only");
        var b=new TaskBundle();BeanUtils.copyProperties(old,b);stamp(b,p);b.setBasedOnBundleId(id);b.setVersionNo(latest+1);b.setRowVersion(0L);b.setContentRevision(0L);b.setStatus("DRAFT");b.setLatestPreviewId(null);b.setUpdatedAt(now());store.bundles.insert(b);
        for(var oldGroup:store.groupRows(old)) {
            var g=new TaskGroup();BeanUtils.copyProperties(oldGroup,g);stamp(g,p);g.setTaskBundleId(b.getId());
            var data=(tools.jackson.databind.node.ObjectNode)JsonValues.read(g.getContentJson()).deepCopy();data.put("changeReason",reason);g.setContentJson(JsonValues.write(data));store.groupRows.insert(g);
            for(var oldVariant:store.variants.selectList(new QueryWrapper<TaskGroupVariant>().eq("org_id",p.orgId()).eq("task_group_id",oldGroup.getId()))) {
                var v=new TaskGroupVariant();BeanUtils.copyProperties(oldVariant,v);stamp(v,p);v.setTaskGroupId(g.getId());store.variants.insert(v);
            }
            for(var oldSource:store.sources.selectList(new QueryWrapper<TaskGroupSource>().eq("org_id",p.orgId()).eq("task_group_id",oldGroup.getId()))) {
                var s=new TaskGroupSource();BeanUtils.copyProperties(oldSource,s);stamp(s,p);s.setTaskGroupId(g.getId());store.sources.insert(s);
            }
        }
        b.setContentHash(store.hash(b));store.bundles.updateById(b);store.audit(p,b,"TASK_BUNDLE_CLONED",null);return store.view(b);
    }
}
