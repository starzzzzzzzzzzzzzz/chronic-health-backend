package com.healthsystem.service.impl;

import com.healthsystem.vo.ManagementCoordinationReportVO;
import com.healthsystem.vo.ManagementCoordinationReportVO.*;
import tools.jackson.databind.JsonNode;
import java.util.*;

/** Auditable structural coverage only; semantic/medical conflict resolution still belongs to review. */
public final class ManagementCoordinationPolicy {
    private ManagementCoordinationPolicy() {}
    public static ManagementCoordinationReportVO report(JsonNode items,JsonNode context) {
        List<Coverage> coverage=new ArrayList<>();List<CoordinationIssue> issues=new ArrayList<>();
        for(var disease:context.path("diseases")) {
            Set<String> sources=new HashSet<>();for(var id:disease.path("sourceFactIds"))sources.add(id.asText());
            boolean covered=false;
            for(var item:items)if(item.path("isSelected").asBoolean(true))for(var basis:item.path("basis"))if(sources.contains(basis.path("factId").asText()))covered=true;
            String name=disease.path("name").asText();
            coverage.add(new Coverage(name,covered?"SOURCE_LINKED":"PENDING_REVIEW",
                    covered?"存在引用该病种来源的建议；仅证明来源关联，适用性和冲突仍须审核。":"本批建议尚未引用该病种来源；请确认是否需要补充，不能为凑覆盖添加无依据任务。",List.of(),List.of()));
            if(!covered)issues.add(new CoordinationIssue("DISEASE_COVERAGE_PENDING",name+"的建议覆盖需要人工核对",List.of()));
        }
        for(var name:context.path("fusion").path("unconfirmedDiseases"))
            issues.add(new CoordinationIssue("MANAGEMENT_CONFIRMATION_PENDING",name.asText()+"的侧重或优先级尚未确认，暂不形成正式融合权重",List.of()));
        Set<String> actions=new HashSet<>();
        for(var item:items)if(item.path("isSelected").asBoolean(true)) {
            String text=item.path("content").asText().replaceAll("\\s+","");
            if(!actions.add(text))issues.add(new CoordinationIssue("DUPLICATE_ADVICE", "检测到相同建议正文，请合并共享行动并保留各病种来源",List.of()));
        }
        return new ManagementCoordinationReportVO(coverage.size()>1||!issues.isEmpty()?"REVIEW_REQUIRED":"SOURCE_CHECKED",
                context.path("sourceHash").asText(),coverage,issues,
                "仅检查来源覆盖和完全重复正文，未自动完成医学冲突、语义重复或执行负担审核。融合状态："+context.path("fusion").path("status").asText());
    }
}
