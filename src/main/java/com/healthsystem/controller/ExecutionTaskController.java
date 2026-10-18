package com.healthsystem.controller;
import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.service.impl.ExecutionTaskService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
@RestController @Tag(name="09 发布与日任务")
public class ExecutionTaskController {
 private final ExecutionTaskService service;public ExecutionTaskController(ExecutionTaskService s){service=s;}
 @GetMapping("/patients/{id}/execution-tasks") @Operation(summary="查询指定患者正式日任务",description="date为业务日期YYYY-MM-DD；省略时按Asia/Shanghai计算今日。每条任务返回自身zoneId。仅已生效正式实例可见。")
 public ApiResponse<List<JsonNode>> list(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestParam(required=false) LocalDate date){return ApiResponse.success(service.list(p,id,date));}
 @GetMapping("/execution-tasks/{id}") @Operation(summary="查询正式任务及冻结执行窗口")
 public ApiResponse<JsonNode> get(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.get(p,id));}
}
