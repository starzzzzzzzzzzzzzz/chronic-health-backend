package com.healthsystem.controller;

import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.*;
import com.healthsystem.vo.*;
import com.healthsystem.service.impl.PlanGenerationService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@Tag(name="11 AI 方案候选")
public class PlanGenerationController {
    private final PlanGenerationService service;
    public PlanGenerationController(PlanGenerationService service){this.service=service;}
    @PostMapping("/plan-versions/{id}/generation-jobs")
    @Operation(summary="提交 AI 方案生成",description="冻结最新 DEMO 患者快照；返回作业供轮询，不覆盖草稿。同 Idempotency-Key 重试不重复调用模型。失败后用新键及 retryOfJobId 发起新作业。")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<ApiResponse<PlanGenerationJobVO>> generate(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,
        @PathVariable String id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody GeneratePlanRequest body,HttpServletRequest request) {
        var result=service.submit(p,id,key,body);
        return ResponseEntity.accepted().location(URI.create(request.getContextPath()+"/plan-generation-jobs/"+result.job().id()))
            .body(new ApiResponse<>(202,"success",result.job()));
    }
    @GetMapping("/plan-generation-jobs/{id}") @Operation(summary="读取生成进度与候选",description="PENDING/RUNNING/SUCCEEDED/REJECTED/FAILED/APPLIED；成功仅代表结构与引用校验通过，仍须人工审核。")
    public ApiResponse<PlanGenerationJobVO> get(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.get(p,id));}
    @GetMapping("/plan-versions/{id}/generation-jobs") @Operation(summary="查询最近 20 次生成历史")
    public ApiResponse<List<PlanGenerationJobVO>> list(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.list(p,id));}
    @PostMapping("/plan-generation-jobs/{id}/adoptions") @Operation(summary="采用候选到草稿",description="expectedRevision 必须与生成时的草稿修订相同。原子替换目标、模块、侧重和七维内容；不会自动送审或发布。生成后已有人工编辑则409，需保留修改并重新生成。")
    public ApiResponse<CarePlanDraftVO> apply(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@Valid @RequestBody ApplyGeneratedPlanRequest body){return ApiResponse.success(service.apply(p,id,body));}
}
