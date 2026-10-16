package com.healthsystem.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.*;
import com.healthsystem.config.ApiDocumentationModels;
import org.springframework.http.HttpStatus;

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
@Tag(name="05 方案版本")
@RestController
public class PlanVersionController {
    private final PlanVersionService service;
    public PlanVersionController(PlanVersionService service){this.service=service;}
    @PostMapping("/care-plans/{id}/versions")
    @Operation(summary="从已批准方案建立新草稿版本", description="仅当前 APPROVED 版本可以建立新草稿。旧版、确认和审核历史保留，新草稿须重新确认和审核。")
 @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(mediaType="application/json", schema=@Schema(implementation=ApiDocumentationModels.NewVersion.class), examples=@ExampleObject(name="调用示例", value="{\"sourceVersionId\": \"替换为当前已批准版本UUID\", \"expectedRevision\": \"4\", \"changeReason\": \"医生主动调整周期目标\"}")))
 @ResponseStatus(HttpStatus.CREATED)
 public ResponseEntity<ApiResponse<CarePlanDraftVO>> create(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@org.springframework.web.bind.annotation.RequestBody ReviewWorkflowRequest body,HttpServletRequest http) {
        var result=service.create(p,id,body);
        return ResponseEntity.created(URI.create(http.getContextPath()+"/plan-versions/"+result.versionId())).body(new ApiResponse<>(201,"success",result));
    }
}
