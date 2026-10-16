package com.healthsystem.controller;
import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.service.impl.TaskPublicationService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import tools.jackson.databind.JsonNode;
@RestController @Tag(name="09 发布与日任务")
public class TaskPublicationController {
 private final TaskPublicationService service;public TaskPublicationController(TaskPublicationService s){service=s;}
 @PostMapping("/task-bundles/{id}/publication-previews") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="核对发布与未来版本衔接清单")
 public ResponseEntity<ApiResponse<JsonNode>> preview(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http){var v=service.preview(p,id,body);return ResponseEntity.created(URI.create(http.getContextPath()+"/task-bundles/"+id+"/publication-previews/"+v.path("id").asText())).body(new ApiResponse<>(201,"success",v));}
 @GetMapping("/task-bundles/{id}/publication-previews/{previewId}") @Operation(summary="读取冻结发布衔接清单")
 public ApiResponse<JsonNode> getPreview(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@PathVariable String previewId){return ApiResponse.success(service.getPreview(p,id,previewId));}
 @PostMapping("/task-bundles/{id}/publications") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="按指定预览事务发布任务",description="首次201，幂等回读200；需 Idempotency-Key，当前任务包修订号及两份预览。")
 public ResponseEntity<ApiResponse<JsonNode>> publish(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http){var v=service.publish(p,id,key,body);return ResponseEntity.status(v.created()?201:200).location(URI.create(http.getContextPath()+"/publications/"+v.data().path("id").asText())).body(new ApiResponse<>(v.created()?201:200,"success",v.data()));}
 @GetMapping("/publications/{id}") @Operation(summary="查询发布结果与通知配置状态")
 public ApiResponse<JsonNode> get(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.get(p,id));}
}
