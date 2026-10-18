package com.healthsystem.controller;

import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.EvaluateManagementRequest;
import com.healthsystem.service.impl.ManagementBasisService;
import com.healthsystem.vo.ManagementBasisVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name="14 病种管理侧重与来源")
public class ManagementBasisController {
    private final ManagementBasisService service;
    public ManagementBasisController(ManagementBasisService service){this.service=service;}
    @PostMapping("/store/care/drafts/basis/evaluate")
    @Operation(summary="按旧契约读取病种管理依据",description="返回当前已确认诊断、文档侧重选项及 sourceHash；本批亚型和优先级保持待人工确认，无模型调用。生成建议时原样传回 evaluation 和 sourceHash，权重为整数百分比（总和100）。")
    public ApiResponse<ManagementBasisVO> legacy(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@Valid @RequestBody EvaluateManagementRequest r){return ApiResponse.success(service.legacy(p,r));}
    @GetMapping("/patients/{id}/management-basis")
    @Operation(summary="读取患者病种管理依据",description="原生 UUID 患者入口；响应 archiveId 字段为本入口患者 UUID。其余结构与旧依据评估相同，结果可用于生成作业的 confirmedManagement。")
    public ApiResponse<ManagementBasisVO> nativeBasis(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.nativeBasis(p,id));}
}
