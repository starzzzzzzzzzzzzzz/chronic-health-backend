package com.healthsystem.controller;
import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.service.ConditionConfirmationService;
import com.healthsystem.vo.ConditionConfirmationVO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
@RestController
public class ConditionConfirmationController {
    private final ConditionConfirmationService service;
    public ConditionConfirmationController(ConditionConfirmationService service){this.service=service;}
    @GetMapping("/plan-versions/{id}/condition-confirmations")
    public ApiResponse<ConditionConfirmationVO> get(@AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.get(p,id));}
    @PostMapping("/plan-versions/{id}/condition-confirmations")
    public ResponseEntity<ApiResponse<ConditionConfirmationVO>> save(@AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http){
        var result=service.save(p,id,body);
        return ResponseEntity.created(URI.create(http.getContextPath()+"/plan-versions/"+id+"/condition-confirmations")).body(new ApiResponse<>(201,"success",result));
    }
}
