package com.healthsystem.controller;

import com.healthsystem.common.api.ApiResponse;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.service.LegacyArchiveService;
import com.healthsystem.vo.LegacyArchivePageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "11 旧接口兼容：档案选择")
@RestController
@RequestMapping("/store/care/grant-administration/archives")
public class LegacyArchiveController {
    private final LegacyArchiveService service;

    public LegacyArchiveController(LegacyArchiveService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "按前端旧契约查询授权档案列表")
    public ApiResponse<LegacyArchivePageVO> list(
            @Parameter(hidden = true) @AuthenticationPrincipal BusinessPrincipal principal,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.list(principal, page, pageSize, keyword));
    }
}
