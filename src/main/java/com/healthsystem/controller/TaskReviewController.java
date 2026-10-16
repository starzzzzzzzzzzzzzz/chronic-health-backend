package com.healthsystem.controller;
import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.service.impl.*;
import com.healthsystem.vo.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;

@RestController @Tag(name="08 排程与任务二审")
public class TaskReviewController {
    private final TaskScheduleService schedules;private final TaskReviewService reviews;private final TaskBundleVersionService versions;
    public TaskReviewController(TaskScheduleService s,TaskReviewService r,TaskBundleVersionService v){schedules=s;reviews=r;versions=v;}
    @PostMapping("/task-bundles/{id}/schedule-previews") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="生成确定周期排程预览")
    public ResponseEntity<ApiResponse<TaskSchedulePreviewVO>> preview(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http) {
        var v=schedules.preview(p,id,body);return ResponseEntity.created(URI.create(http.getContextPath()+"/schedule-previews/"+v.id())).body(new ApiResponse<>(201,"success",v));
    }
    @GetMapping("/schedule-previews/{id}") @Operation(summary="读取冻结排程与阻断项")
    public ApiResponse<TaskSchedulePreviewVO> get(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(schedules.get(p,id));}
    @GetMapping("/task-bundles/{id}/review-context") @Operation(summary="查询任务二审资料与历史")
    public ApiResponse<Map<String,Object>> context(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(reviews.context(p,id));}
    @PostMapping("/task-bundles/{id}/review-submissions") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="提交任务二审",description="expectedRevision 为任务包修订号；schedulePreviewId 必须是当前内容最新的无阻断预览。")
    public ResponseEntity<ApiResponse<ReviewSubmissionVO>> submit(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http) {
        var v=reviews.submit(p,id,body);return ResponseEntity.created(URI.create(http.getContextPath()+"/review-submissions/"+v.id())).body(new ApiResponse<>(201,"success",v));
    }
    @PostMapping("/task-bundles/{id}/versions") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="从已批准任务包建立新草稿版本")
    public ResponseEntity<ApiResponse<TaskBundleVO>> version(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http) {
        var v=versions.create(p,id,body);return ResponseEntity.created(URI.create(http.getContextPath()+"/task-bundles/"+v.id())).body(new ApiResponse<>(201,"success",v));
    }
}
