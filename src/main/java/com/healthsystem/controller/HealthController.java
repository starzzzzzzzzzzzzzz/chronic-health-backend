package com.healthsystem.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


import com.healthsystem.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="06 运行状态")
@RestController
public class HealthController {
    /** 进程存活检查；数据库就绪状态使用 /actuator/health。 */
    @GetMapping("/health")
    @Operation(summary="检查服务存活")
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.success(new HealthStatus("UP", "chronic-health"));
    }
    public record HealthStatus(String status, String service) {}
}
