package com.healthsystem.controller;
import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.service.impl.TaskFeedbackService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import tools.jackson.databind.JsonNode;
@RestController @Tag(name="10 打卡与补记")
public class TaskFeedbackController {
 private final TaskFeedbackService service;public TaskFeedbackController(TaskFeedbackService s){service=s;}
 @PostMapping("/execution-tasks/{id}/check-ins") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="一键完成或提交基础反馈",description="空对象默认自述COMPLETED。实际时间与反馈均选填；服务端区分正常提交与补记。首次201，幂等回读200。")
 public ResponseEntity<ApiResponse<JsonNode>> checkIn(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http){return response(service.checkIn(p,id,key,body),http);}
 @PostMapping("/check-ins/{id}/amendments") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="追加反馈更正",description="id为最新反馈ID，expectedRevision为currentResult.rowVersion。省略字段保留；不覆盖历史，不清除未处置安全事件。")
 public ResponseEntity<ApiResponse<JsonNode>> amend(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http){return response(service.amend(p,id,key,body),http);}
 @GetMapping("/execution-tasks/{id}/check-ins") @Operation(summary="查询任务全部反馈版本")
 public ApiResponse<List<JsonNode>> history(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.history(p,id));}
 @GetMapping("/check-ins/{id}") @Operation(summary="读取一条不可变反馈")
 public ApiResponse<JsonNode> get(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.get(p,id));}
 private ResponseEntity<ApiResponse<JsonNode>> response(TaskFeedbackService.Written v,HttpServletRequest http){return ResponseEntity.status(v.created()?201:200).location(URI.create(http.getContextPath()+"/check-ins/"+v.data().path("id").asText())).body(new ApiResponse<>(v.created()?201:200,"success",v.data()));}
}
