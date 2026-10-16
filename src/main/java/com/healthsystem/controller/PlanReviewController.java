package com.healthsystem.controller;
import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.service.PlanReviewService;
import com.healthsystem.vo.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
@RestController
public class PlanReviewController {
    private final PlanReviewService service;
    public PlanReviewController(PlanReviewService service){this.service=service;}
    @GetMapping("/plan-versions/{id}/review-context")
    public ApiResponse<PlanReviewContextVO> context(@AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.context(p,id));}
    @PostMapping("/plan-versions/{id}/review-submissions")
    public ResponseEntity<ApiResponse<ReviewSubmissionVO>> submit(@AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http){return created(service.submit(p,id,body),http,"");}
    @GetMapping("/review-submissions/{id}")
    public ApiResponse<ReviewSubmissionVO> get(@AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.getSubmission(p,id));}
    @PostMapping("/review-submissions/{id}/decisions")
    public ResponseEntity<ApiResponse<ReviewSubmissionVO>> decide(@AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http){
        var result=service.decide(p,id,body);return created(result,http,"");
    }
    @PostMapping("/review-submissions/{id}/withdrawals")
    public ResponseEntity<ApiResponse<ReviewSubmissionVO>> withdraw(@AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http){return created(service.withdraw(p,id,body),http,"");}
    private ResponseEntity<ApiResponse<ReviewSubmissionVO>> created(ReviewSubmissionVO result,HttpServletRequest http,String suffix){
        return ResponseEntity.created(URI.create(http.getContextPath()+"/review-submissions/"+result.id()+suffix)).body(new ApiResponse<>(201,"success",result));
    }
}
