package com.healthsystem.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.*;
import com.healthsystem.config.ApiDocumentationModels;
import org.springframework.http.HttpStatus;

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
@Tag(name="04 方案一审")
@RestController
public class PlanReviewController {
    private final PlanReviewService service;
    public PlanReviewController(PlanReviewService service){this.service=service;}
    @GetMapping("/plan-versions/{id}/review-context")
    @Operation(summary="查询一审资料与阻断项")
 public ApiResponse<PlanReviewContextVO> context(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.context(p,id));}
    @PostMapping("/plan-versions/{id}/review-submissions")
    @Operation(summary="提交方案一审", description="先查询 review-context，解决 BLOCKER 后提交。修订号属于方案；成功后待审正文冻结。")
 @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(mediaType="application/json", schema=@Schema(implementation=ApiDocumentationModels.Submission.class), examples=@ExampleObject(name="调用示例", value="{\"expectedRevision\": \"2\"}")))
 @ResponseStatus(HttpStatus.CREATED)
 public ResponseEntity<ApiResponse<ReviewSubmissionVO>> submit(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@org.springframework.web.bind.annotation.RequestBody ReviewWorkflowRequest body,HttpServletRequest http){return created(service.submit(p,id,body),http,"");}
    @GetMapping("/review-submissions/{id}")
    @Operation(summary="读取本次送审快照与结果")
 public ApiResponse<ReviewSubmissionVO> get(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.getSubmission(p,id));}
    @PostMapping("/review-submissions/{id}/decisions")
    @Operation(summary="审核通过或退回", description="必须切换为独立审核身份。修订号属于审核提交，禁止提交人自审；RETURN 必须填写 comment。")
 @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(mediaType="application/json", schema=@Schema(implementation=ApiDocumentationModels.Decision.class), examples=@ExampleObject(name="调用示例", value="{\"expectedRevision\": \"0\", \"decision\": \"APPROVE\", \"comment\": \"已核对目标、依据、禁忌及执行主体\"}")))
 @ResponseStatus(HttpStatus.CREATED)
 public ResponseEntity<ApiResponse<ReviewSubmissionVO>> decide(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@org.springframework.web.bind.annotation.RequestBody ReviewWorkflowRequest body,HttpServletRequest http){
        var result=service.decide(p,id,body);return created(result,http,"");
    }
    @PostMapping("/review-submissions/{id}/withdrawals")
    @Operation(summary="提交人撤回待审方案", description="仅提交人本人可撤回 PENDING 审核。使用审核提交的 rowVersion；历史快照保留。")
 @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(mediaType="application/json", schema=@Schema(implementation=ApiDocumentationModels.Withdrawal.class), examples=@ExampleObject(name="调用示例", value="{\"expectedRevision\": \"0\", \"reason\": \"补充资料后重新提交\"}")))
 @ResponseStatus(HttpStatus.CREATED)
 public ResponseEntity<ApiResponse<ReviewSubmissionVO>> withdraw(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@org.springframework.web.bind.annotation.RequestBody ReviewWorkflowRequest body,HttpServletRequest http){return created(service.withdraw(p,id,body),http,"");}
    private ResponseEntity<ApiResponse<ReviewSubmissionVO>> created(ReviewSubmissionVO result,HttpServletRequest http,String suffix){
        return ResponseEntity.created(URI.create(http.getContextPath()+"/review-submissions/"+result.id()+suffix)).body(new ApiResponse<>(201,"success",result));
    }
}
