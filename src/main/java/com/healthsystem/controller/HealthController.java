package com.healthsystem.controller;

import com.healthsystem.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    /** 进程存活检查；数据库就绪状态使用 /actuator/health。 */
    @GetMapping("/health")
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.success(new HealthStatus("UP", "chronic-health"));
    }
    public record HealthStatus(String status, String service) {}
}
