package com.healthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.healthsystem.common.json.JsonValues;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.entity.*;
import com.healthsystem.mapper.*;
import com.healthsystem.service.PlanAiClient;
import com.healthsystem.exception.BusinessException;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.Map;

/** Durable one-at-a-time queue. External call runs without an open DB transaction. */
@Component
public class PlanGenerationWorker {
    private final PlanGenerationJobMapper jobs;
    private final HealthInputSnapshotMapper snapshots;
    private final PlanGenerationService service;
    private final PlanAiClient client;
    private final AiPlanPolicy policy;
    private final Environment env;
    public PlanGenerationWorker(PlanGenerationJobMapper j,HealthInputSnapshotMapper s,PlanGenerationService service,PlanAiClient c,AiPlanPolicy p,Environment env){jobs=j;snapshots=s;this.service=service;client=c;policy=p;this.env=env;}
    @Scheduled(fixedDelayString="${health.ai.poll-ms:1000}",initialDelay=2000)
    public void poll() {
        if(!env.getProperty("health.ai.enabled",Boolean.class,false)||!env.getProperty("health.ai.worker-enabled",Boolean.class,true))return;
        // Interrupted/uncertain requests are failed, not automatically billed again after restart.
        jobs.update(null,new UpdateWrapper<PlanGenerationJob>().eq("status","RUNNING")
            .lt("started_at",LocalDateTime.now(Clock.systemUTC()).minusMinutes(5))
            .set("status","FAILED").set("error_code","AI_INTERRUPTED").set("finished_at",LocalDateTime.now(Clock.systemUTC())));
        var pending=jobs.selectList(new QueryWrapper<PlanGenerationJob>().eq("status","PENDING").orderByAsc("created_at","id").last("LIMIT 1"));
        if(!pending.isEmpty())process(pending.get(0).getId());
    }
    public void process(String id) {
        int claimed=jobs.update(null,new UpdateWrapper<PlanGenerationJob>().eq("id",id).eq("status","PENDING")
            .set("status","RUNNING").set("started_at",LocalDateTime.now(Clock.systemUTC())));
        if(claimed!=1)return;
        var job=jobs.selectById(id);
        var finish=new UpdateWrapper<PlanGenerationJob>().eq("id",id).eq("status","RUNNING");
        try {
            if(!"DEMO".equals(job.getDataMode()))throw new DeepSeekPlanClient.AiFailure("AI_MODE_NOT_ALLOWED");
            if(!AiPlanPolicy.VERSION.equals(job.getRuleVersion()))throw new DeepSeekPlanClient.AiFailure("AI_RULE_VERSION_CHANGED");
            service.require(new BusinessPrincipal(job.getOrgId(),job.getCreatedBy(),job.getDataMode()),id);
            var result=client.generate(job.getModel(),job.getSystemPrompt(),job.getModelInputJson());
            if(result.content()==null||result.content().length()>100_000)throw new DeepSeekPlanClient.AiFailure("AI_RESPONSE_TOO_LARGE");
            var usage=JsonValues.read(result.usageJson());
            var recordedUsage=usage!=null&&usage.isObject()?(tools.jackson.databind.node.ObjectNode)usage.deepCopy():(tools.jackson.databind.node.ObjectNode)JsonValues.tree(Map.of());
            recordedUsage.put("providerModel",result.model());
            finish.set("raw_output",result.content()).set("usage_json",JsonValues.write(recordedUsage)).set("provider_request_id",result.requestId());
            var snapshot=snapshots.selectOne(new QueryWrapper<HealthInputSnapshot>().eq("org_id",job.getOrgId()).eq("id",job.getInputSnapshotId()));
            try {
                var patch=policy.validate(result.content(),JsonValues.read(snapshot.getPayloadJson()));
                var content=JsonValues.read(result.content());
                finish.set("status","SUCCEEDED").set("candidate_json",JsonValues.write(patch))
                    .set("issues_json",JsonValues.write(Map.of("missingInformation",content.path("missingInformation"),"warnings",content.path("warnings"),"normalizations",policy.normalizations(content,patch))));
            } catch(BusinessException e) {
                finish.set("status","REJECTED").set("error_code","AI_OUTPUT_REJECTED")
                    .set("issues_json",JsonValues.write(Map.of("validationErrors",java.util.List.of(e.getMessage()))));
            }
        } catch(DeepSeekPlanClient.AiFailure e) {finish.set("status","FAILED").set("error_code",e.getMessage());}
        catch(BusinessException e){finish.set("status","FAILED").set("error_code","AI_ACCESS_REVOKED");}
        catch(Exception e){finish.set("status","FAILED").set("error_code","AI_INTERNAL_ERROR");}
        finish.set("finished_at",LocalDateTime.now(Clock.systemUTC()));jobs.update(null,finish);
    }
}
