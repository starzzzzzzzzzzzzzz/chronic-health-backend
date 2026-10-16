package com.healthsystem.common.api;

/** 公共响应；业务 ID 在 DTO 中使用 String，避免前端长整数精度丢失。 */
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }
}
