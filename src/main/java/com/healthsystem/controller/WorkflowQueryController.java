package com.healthsystem.controller;

import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.service.impl.WorkflowQueryService;
import com.healthsystem.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@Tag(name="11 工作台与流程查询")
public class WorkflowQueryController {
    private final WorkflowQueryService service;
    public WorkflowQueryController(WorkflowQueryService service){this.service=service;}
    @GetMapping("/care-plans")
    @Operation(summary="查询授权患者的方案列表",description="每个方案返回当前版本；按更新时间及版本ID倒序，权限过滤先于分页。status为当前版本状态。")
    public ApiResponse<PageVO<Map<String,Object>>> plans(@AuthenticationPrincipal BusinessPrincipal p,@RequestParam(required=false) String patientId,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(service.plans(p,patientId,status,page,size));}
    @GetMapping("/review-submissions")
    @Operation(summary="查询审核待办或我的提交",description="scope=pending默认只返回有审核权限、非本人提交的待审记录；scope=mine返回本人全部状态的提交。stage可选PLAN或TASK。")
    public ApiResponse<PageVO<Map<String,Object>>> reviews(@AuthenticationPrincipal BusinessPrincipal p,@RequestParam(defaultValue="pending") String scope,@RequestParam(required=false) String stage,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(service.reviews(p,scope,stage,page,size));}
    @GetMapping("/plan-versions/{id}/workflow")
    @Operation(summary="恢复方案版本关联业务流程",description="需PATIENT_READ及TASK_READ。返回同方案全部版本，以及指定版本的全部任务包、审核提交和发布元数据。空数组表示无记录；不得将当前新草稿与旧版本发布混同。可用返回ID调用既有详情接口。")
    public ApiResponse<Map<String,Object>> workflow(@AuthenticationPrincipal BusinessPrincipal p,@PathVariable String id){return ApiResponse.success(service.workflow(p,id));}
}
