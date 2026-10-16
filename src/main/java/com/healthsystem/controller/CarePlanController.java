package com.healthsystem.controller;
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
@RestController
public class CarePlanController {
 private final CarePlanService service;
 public CarePlanController(CarePlanService service){this.service=service;}
 @PostMapping("/care-plans")
 public ResponseEntity<ApiResponse<CarePlanDraftVO>> create(@AuthenticationPrincipal BusinessPrincipal principal,@Valid @RequestBody CreateCarePlanRequest request,HttpServletRequest http) {
  var result=service.create(principal,request);
  String prefix=http.getContextPath().isBlank()?"/ncd-ai":http.getContextPath();
  return ResponseEntity.created(URI.create(prefix+"/plan-versions/"+result.versionId())).body(new ApiResponse<>(201,"success",result));
 }
 @GetMapping("/plan-versions/{id}") public ApiResponse<CarePlanDraftVO> get(@AuthenticationPrincipal BusinessPrincipal principal,@PathVariable String id){return ApiResponse.success(service.get(principal,id));}
 @PatchMapping("/plan-versions/{id}") public ApiResponse<CarePlanDraftVO> update(@AuthenticationPrincipal BusinessPrincipal principal,@PathVariable String id,@RequestBody UpdateCarePlanRequest request){return ApiResponse.success(service.update(principal,id,request));}
}
