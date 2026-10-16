package com.healthsystem.controller;
import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.service.PatientService;
import com.healthsystem.vo.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/patients")
public class PatientController {
    private final PatientService service;
    public PatientController(PatientService service) {this.service=service;}
    @GetMapping public ApiResponse<PageVO<PatientSummaryVO>> list(@AuthenticationPrincipal BusinessPrincipal principal,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String q) {return ApiResponse.success(service.list(principal,page,size,q));}
    @GetMapping("/{id}/health-summary") public ApiResponse<PatientHealthSummaryVO> detail(@AuthenticationPrincipal BusinessPrincipal principal,@PathVariable String id) {return ApiResponse.success(service.healthSummary(principal,id));}
}
