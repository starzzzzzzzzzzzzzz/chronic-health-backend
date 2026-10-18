package com.healthsystem.service;

public interface PlanAiClient {
    record Result(String content,String model,String requestId,String usageJson) {}
    Result generate(String model,String system,String input);
}
