package com.healthsystem.controller;

import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.service.impl.LegacyCareInputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@Tag(name = "12 旧接口兼容：资料与建议")
public class LegacyCareInputController {
    private final LegacyCareInputService service;
    public LegacyCareInputController(LegacyCareInputService service) { this.service = service; }
    @GetMapping("/store/care/archives/{archiveId}/input")
    @Operation(summary = "按旧档案编号读取患者输入", description = "旧规范的 data 为动态对象。本机返回 scope、patient、profileRevision、axes、facts、conflicts、missingFields；具体页面取值仍待伙伴前端联调确认。GET 不创建快照。")
    public ApiResponse<Map<String, Object>> input(
            @Parameter(hidden = true) @AuthenticationPrincipal BusinessPrincipal principal,
            @PathVariable String archiveId) {
        return ApiResponse.success(service.input(principal, archiveId));
    }
}
