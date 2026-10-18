package com.healthsystem.controller;
import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.ReviewWorkflowRequest;
import com.healthsystem.service.TaskBundleService;
import com.healthsystem.vo.TaskBundleVO;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

@RestController @Tag(name="07 七维任务包")
public class TaskBundleController {
    private final TaskBundleService service;
    public TaskBundleController(TaskBundleService s){service=s;}
    @PostMapping("/plan-versions/{id}/task-bundles") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="从已审方案派生七维任务草稿",description="确定性规则拆解；保留一审来源并重新读取执行资料。expectedRevision 属于来源方案。")
    public ResponseEntity<ApiResponse<TaskBundleVO>> generate(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestBody ReviewWorkflowRequest body,HttpServletRequest http){
        var value=service.generate(p,id,body);return ResponseEntity.created(URI.create(http.getContextPath()+"/task-bundles/"+value.id())).body(new ApiResponse<>(201,"success",value));
    }
    @GetMapping("/task-bundles/{id}") @Operation(summary="读取任务包与来源")
    public ApiResponse<TaskBundleVO> get(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.get(p,id));}
    @PatchMapping("/task-bundles/{id}") @Operation(summary="编辑任务执行编排",description="仅任务草稿可编辑。source、医学内容、目标和主体不可覆盖；expectedRevision 属于任务包。")
    public ApiResponse<TaskBundleVO> update(@Parameter(hidden=true) @AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id,@RequestBody ReviewWorkflowRequest body){return ApiResponse.success(service.update(p,id,body));}
}
