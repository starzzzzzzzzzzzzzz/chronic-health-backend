package com.healthsystem.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.*;
import com.healthsystem.config.ApiDocumentationModels;
import org.springframework.http.HttpStatus;

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
@Tag(name="03 执行条件")
@RestController
public class ConditionConfirmationController {
    private final ConditionConfirmationService service;
    public ConditionConfirmationController(ConditionConfirmationService service){this.service=service;}
    @GetMapping("/plan-versions/{id}/condition-confirmations")
    @Operation(summary="查询执行条件及确认历史")
 public ApiResponse<ConditionConfirmationVO> get(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.get(p,id));}
    @PostMapping("/plan-versions/{id}/condition-confirmations")
    @Operation(summary="追加执行条件确认", description="先 GET 查询服务端派生的 conditionKey。确认人和时间由服务端记录。医嘱来源引用只提供 sourceFactId，不上传专业 value。")
 @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(mediaType="application/json", schema=@Schema(implementation=ApiDocumentationModels.ConfirmationBatch.class), examples=@ExampleObject(name="调用示例", value="{\"expectedRevision\": \"1\", \"entries\": [{\"conditionKey\": \"execution.替换为实际条目UUID\", \"status\": \"CONFIRMED\", \"value\": {\"actorType\": \"PATIENT\", \"available\": true, \"environmentConfirmed\": true}, \"confirmationNote\": \"已人工核实执行主体和环境\"}]}")))
 @ResponseStatus(HttpStatus.CREATED)
 public ResponseEntity<ApiResponse<ConditionConfirmationVO>> save(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@org.springframework.web.bind.annotation.RequestBody ReviewWorkflowRequest body,HttpServletRequest http){
        var result=service.save(p,id,body);
        return ResponseEntity.created(URI.create(http.getContextPath()+"/plan-versions/"+id+"/condition-confirmations")).body(new ApiResponse<>(201,"success",result));
    }
}
