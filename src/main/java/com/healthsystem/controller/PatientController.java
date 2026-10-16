package com.healthsystem.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.service.PatientService;
import com.healthsystem.vo.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@Tag(name="01 患者资料")
@RestController
@RequestMapping("/patients")
public class PatientController {
    private final PatientService service;
    public PatientController(PatientService service) {this.service=service;}
    @GetMapping @Operation(summary="查询授权患者列表")
 public ApiResponse<PageVO<PatientSummaryVO>> list(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal principal,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String q) {return ApiResponse.success(service.list(principal,page,size,q));}
    @GetMapping("/{id}/health-summary") @Operation(summary="读取患者六方面健康摘要")
 public ApiResponse<PatientHealthSummaryVO> detail(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal principal,@PathVariable String id) {return ApiResponse.success(service.healthSummary(principal,id));}
}
