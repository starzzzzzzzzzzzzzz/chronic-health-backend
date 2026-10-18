package com.healthsystem.service.impl;

import com.healthsystem.common.json.JsonValues;
import com.healthsystem.service.PlanAiClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** No patient/prompt/key logging; no implicit retries of a billable generation. */
@Component
public class DeepSeekPlanClient implements PlanAiClient {
    private final Environment env;
    private final HttpClient http;
    @org.springframework.beans.factory.annotation.Autowired
    public DeepSeekPlanClient(Environment env){this(env,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());}
    DeepSeekPlanClient(Environment env,HttpClient http){this.env=env;this.http=http;}
    @Override public Result generate(String model,String system,String input) {
        String key=env.getProperty("health.ai.api-key","");
        if(key.isBlank())throw new AiFailure("AI_NOT_CONFIGURED");
        String base=env.getProperty("health.ai.base-url","https://api.deepseek.com");
        if(!"https://api.deepseek.com".equals(base))throw new AiFailure("INVALID_AI_ENDPOINT");
        var body=new LinkedHashMap<String,Object>();
        body.put("model",model);
        body.put("messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",input)));
        body.put("response_format",Map.of("type","json_object"));body.put("thinking",Map.of("type","disabled"));
        body.put("temperature",0.2);body.put("max_tokens",8000);body.put("stream",false);
        try {
            var request=HttpRequest.newBuilder(URI.create(base+"/chat/completions")).timeout(Duration.ofSeconds(90))
                .header("Authorization","Bearer "+key).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonValues.write(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new AiFailure(switch(response.statusCode()){
                case 401,403->"AI_AUTH_FAILED";case 402->"AI_INSUFFICIENT_BALANCE";case 429->"AI_RATE_LIMITED";default->"AI_UPSTREAM_FAILED";});
            if(response.body().length()>300_000)throw new AiFailure("AI_RESPONSE_TOO_LARGE");
            var data=JsonValues.read(response.body());var choice=data.path("choices").path(0);
            if(!"stop".equals(choice.path("finish_reason").asText()))throw new AiFailure("AI_OUTPUT_INCOMPLETE");
            var content=choice.path("message").path("content");
            if(!content.isString()||content.asText().isBlank())throw new AiFailure("AI_EMPTY_OUTPUT");
            return new Result(content.asText(),data.path("model").asText(),data.path("id").asText(),JsonValues.write(data.path("usage")));
        } catch(AiFailure e){throw e;}
        catch(HttpTimeoutException e){throw new AiFailure("AI_TIMEOUT");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AiFailure("AI_INTERRUPTED");}
        catch(Exception e){throw new AiFailure("AI_CONNECTION_OR_RESPONSE_ERROR");}
    }
    public static class AiFailure extends RuntimeException { public AiFailure(String code){super(code);} }
}
