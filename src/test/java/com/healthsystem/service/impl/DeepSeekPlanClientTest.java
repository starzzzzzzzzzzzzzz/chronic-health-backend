package com.healthsystem.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeepSeekPlanClientTest {
    private final MockEnvironment env=new MockEnvironment().withProperty("health.ai.api-key","fake-unit-test-key");
    @SuppressWarnings("unchecked")
    private HttpClient response(int status,String body) throws Exception {
        var client=mock(HttpClient.class);HttpResponse<String> reply=mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(status);when(reply.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(reply);return client;
    }
    @Test void returnsJsonContentAndUsageWithoutExposingReasoning() throws Exception {
        var client=new DeepSeekPlanClient(env,response(200,"""
          {"id":"provider-id","model":"deepseek-flash","choices":[{"finish_reason":"stop","message":{"content":"{}","reasoning_content":"not persisted"}}],"usage":{"total_tokens":12}}
          """));
        var result=client.generate("deepseek-flash","JSON prompt","fictional input");
        assertThat(result.content()).isEqualTo("{}");assertThat(result.usageJson()).contains("12");
    }
    @Test void truncationDoesNotMasqueradeAsSuccess() throws Exception {
        var client=new DeepSeekPlanClient(env,response(200,"{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}"));
        assertThatThrownBy(()->client.generate("deepseek-flash","s","u")).hasMessage("AI_OUTPUT_INCOMPLETE");
    }
    @Test void providerErrorsAreSafeAndNeverRetried() throws Exception {
        var http=response(429,"upstream sensitive diagnostic");
        assertThatThrownBy(()->new DeepSeekPlanClient(env,http).generate("deepseek-flash","s","u")).hasMessage("AI_RATE_LIMITED");
        verify(http,times(1)).send(any(),any());
    }
    @Test void incorrectEndpointIsRejectedBeforeCredentialTransmission() {
        var http=mock(HttpClient.class);env.withProperty("health.ai.base-url","https://untrusted.invalid");
        assertThatThrownBy(()->new DeepSeekPlanClient(env,http).generate("deepseek-flash","s","u")).hasMessage("INVALID_AI_ENDPOINT");
        verifyNoInteractions(http);
    }
}
