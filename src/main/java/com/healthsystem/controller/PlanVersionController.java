package com.healthsystem.controller;
import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.service.PlanVersionService;
import com.healthsystem.vo.CarePlanDraftVO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
@RestController
public class PlanVersionController {
    private final PlanVersionService service;
    public PlanVersionController(PlanVersionService service){this.service=service;}
    @PostMapping("/care-plans/{id}/versions")
    public ResponseEntity<ApiResponse<CarePlanDraftVO>> create(@AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http) {
        var result=service.create(p,id,body);
        return ResponseEntity.created(URI.create(http.getContextPath()+"/plan-versions/"+result.versionId())).body(new ApiResponse<>(201,"success",result));
    }
}
