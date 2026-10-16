package com.healthsystem.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.*;
import com.healthsystem.config.ApiDocumentationModels;
import org.springframework.http.HttpStatus;

import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.*;
import com.healthsystem.service.CarePlanService;
import com.healthsystem.vo.CarePlanDraftVO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
@Tag(name="02 方案草稿")
@RestController
public class CarePlanController {
 private final CarePlanService service;
 public CarePlanController(CarePlanService service){this.service=service;}
 @PostMapping("/care-plans")
 @Operation(summary="新建方案草稿", description="先查询患者摘要，expectedProfileRevision 使用返回值。成功返回 201 和新版本 Location。")
 @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(mediaType="application/json", schema=@Schema(implementation=com.healthsystem.dto.CreateCarePlanRequest.class), examples=@ExampleObject(name="调用示例", value="{\"patientId\": \"00000000-0000-4000-8000-000000000001\", \"expectedProfileRevision\": \"1\", \"title\": \"本地演示健康方案\", \"startDate\": \"2026-10-17\", \"endDate\": \"2026-10-23\", \"zoneId\": \"Asia/Shanghai\"}")))
 @ResponseStatus(HttpStatus.CREATED)
 public ResponseEntity<ApiResponse<CarePlanDraftVO>> create(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal principal,@Valid @org.springframework.web.bind.annotation.RequestBody CreateCarePlanRequest request,HttpServletRequest http) {
  var result=service.create(principal,request);
  String prefix=http.getContextPath().isBlank()?"/ncd-ai":http.getContextPath();
  return ResponseEntity.created(URI.create(prefix+"/plan-versions/"+result.versionId())).body(new ApiResponse<>(201,"success",result));
 }
 @GetMapping("/plan-versions/{id}") @Operation(summary="读取方案版本")
 public ApiResponse<CarePlanDraftVO> get(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal principal,@PathVariable String id){return ApiResponse.success(service.get(principal,id));}
 @PatchMapping("/plan-versions/{id}") @Operation(summary="编辑方案草稿", description="仅 DRAFT 可修改。expectedRevision 使用当前方案 rowVersion；省略字段保持原值，空数组清空集合，正文变化使旧条件确认失效。")
 @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(mediaType="application/json", schema=@Schema(implementation=ApiDocumentationModels.PlanPatch.class), examples=@ExampleObject(name="调用示例", value="{\"expectedRevision\": \"0\", \"title\": \"调整后的演示方案\"}")))
 public ApiResponse<CarePlanDraftVO> update(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal principal,@PathVariable String id,@org.springframework.web.bind.annotation.RequestBody UpdateCarePlanRequest request){return ApiResponse.success(service.update(principal,id,request));}
}
