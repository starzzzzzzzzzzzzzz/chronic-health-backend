package com.healthsystem.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.TaskTemplateMapper;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Structural DEMO scaffolds, never a claim that clinical contents have been medically approved. */
@Component
public class TaskTemplateCatalog implements ApplicationRunner {
    public static final String RULE_VERSION="task-rules-demo-v1";
    private final TaskTemplateMapper templates;private final Environment environment;
    public TaskTemplateCatalog(TaskTemplateMapper t,Environment e){templates=t;environment=e;}
    @Override @Transactional public void run(ApplicationArguments args) {
        if(!Arrays.asList(environment.getActiveProfiles()).contains("local") || !environment.getProperty("health.demo.enabled",Boolean.class,false))return;
        for(String dimension:new TreeSet<>(PlanDraftValidation.DIMENSIONS)) {
            String id=UUID.nameUUIDFromBytes(("demo-task-template:"+dimension).getBytes(StandardCharsets.UTF_8)).toString();
            if(templates.selectById(id)!=null)continue;
            var t=new TaskTemplate();t.setId(id);t.setOrgId("demo-org");t.setCreatedAt(PlanReviewSupport.now());t.setCreatedBy("system:demo");
            t.setTemplateCode("demo-"+dimension);t.setDimensionCode(dimension);t.setVersionNo(1);t.setReviewStatus("DEMO_ONLY");t.setDataMode("DEMO");
            var payload=JsonValues.tree(Map.of("schemaVersion",RULE_VERSION,"fictional",true,"sourceContentOnly",true,
                "feedbackFields",List.of("actualExecutedAt","note","reportedSafety"),"required",List.of(),"variants",List.of(),
                "stopConditions",List.of("出现不适时停止本次执行，并联系服务医护核实")));
            t.setContentJson(JsonValues.write(payload));t.setContentHash(CanonicalJson.hash(payload));templates.insert(t);
        }
    }
    public TaskTemplate find(String org,String mode,String dimension) {
        return templates.selectOne(new QueryWrapper<TaskTemplate>().eq("org_id",org).eq("data_mode",mode).eq("dimension_code",dimension)
                .ne("review_status","DISABLED").orderByDesc("version_no").last("LIMIT 1"));
    }
    public boolean current(String org,tools.jackson.databind.JsonNode binding) {
        var t=templates.selectOne(new QueryWrapper<TaskTemplate>().eq("org_id",org).eq("id",binding.path("id").asText()));
        return t!=null && !"DISABLED".equals(t.getReviewStatus()) && t.getContentHash().equals(binding.path("contentHash").asText())
                && CanonicalJson.hash(JsonValues.read(t.getContentJson())).equals(t.getContentHash());
    }
}
