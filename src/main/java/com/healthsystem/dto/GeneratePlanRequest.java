package com.healthsystem.dto;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.*;
public record GeneratePlanRequest(@NotBlank String expectedRevision,@NotBlank String expectedProfileRevision,
                                 @Size(max=36) String retryOfJobId,
                                 @ArraySchema(arraySchema=@Schema(description="与旧建议入口相同的来源绑定侧重；从患者 management-basis 取得 evaluation/sourceHash"),schema=@Schema(implementation=ConfirmedManagementRequest.class),maxItems=20) JsonNode confirmedManagement) {
    public GeneratePlanRequest(String expectedRevision,String expectedProfileRevision,String retryOfJobId) {
        this(expectedRevision,expectedProfileRevision,retryOfJobId,null);
    }
}
