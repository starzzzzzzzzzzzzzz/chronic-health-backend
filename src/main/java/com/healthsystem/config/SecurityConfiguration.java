package com.healthsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import com.healthsystem.common.security.DemoAuthenticationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        boolean demo = Arrays.asList(environment.getActiveProfiles()).contains("local")
                && environment.getProperty("health.demo.enabled", Boolean.class, false);
        if (demo) {
            http.addFilterBefore(new DemoAuthenticationFilter(environment.getProperty("health.demo.token"), environment.getProperty("health.demo.reviewer-token"), environment.getProperty("health.demo.patient-token")), AnonymousAuthenticationFilter.class);
        }
        boolean docs = Arrays.asList(environment.getActiveProfiles()).contains("local")
                && environment.getProperty("health.docs.enabled", Boolean.class, false);
        if (docs) {
            http.authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET,
                    "/doc.html", "/webjars/**", "/img/icons/**", "/swagger-ui.html", "/swagger-ui/**",
                    "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll());
        }
        // Real authentication is not integrated. Only explicit local DEMO can authenticate here.
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/health", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/store/care/grant-administration/archives").authenticated()
                        .requestMatchers(HttpMethod.GET, "/store/care/archives/*/input").authenticated()
                        .requestMatchers(HttpMethod.POST, "/store/care/drafts/recommendations/generate", "/store/care/drafts/basis/evaluate").authenticated()
                        .requestMatchers(HttpMethod.GET, "/care-plans", "/review-submissions", "/plan-versions/*/workflow").authenticated()
                        .requestMatchers(HttpMethod.GET, "/patients", "/patients/**", "/plan-versions/*", "/plan-versions/*/condition-confirmations", "/plan-versions/*/review-context", "/review-submissions/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/plan-versions/*/condition-confirmations", "/plan-versions/*/review-submissions", "/review-submissions/*/decisions", "/review-submissions/*/withdrawals").authenticated()
                        .requestMatchers(HttpMethod.POST, "/care-plans", "/care-plans/*/versions").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/plan-versions/*").authenticated()
                        .requestMatchers(HttpMethod.POST,"/plan-versions/*/task-bundles").authenticated()
                        .requestMatchers(HttpMethod.GET,"/task-bundles/*").authenticated()
                        .requestMatchers(HttpMethod.PATCH,"/task-bundles/*").authenticated()
                        .requestMatchers(HttpMethod.GET,"/task-bundles/*/review-context","/schedule-previews/*").authenticated()
                        .requestMatchers(HttpMethod.POST,"/task-bundles/*/schedule-previews","/task-bundles/*/review-submissions","/task-bundles/*/versions").authenticated()
                        .requestMatchers(HttpMethod.POST,"/task-bundles/*/publication-previews","/task-bundles/*/publications").authenticated()
                        .requestMatchers(HttpMethod.GET,"/publications/*","/execution-tasks/*","/task-bundles/*/publication-previews/*").authenticated()
                        .requestMatchers(HttpMethod.POST,"/execution-tasks/*/check-ins","/check-ins/*/amendments").authenticated()
                        .requestMatchers(HttpMethod.GET,"/execution-tasks/*/check-ins","/check-ins/*").authenticated()
                        .requestMatchers(HttpMethod.POST,"/plan-versions/*/generation-jobs","/plan-generation-jobs/*/adoptions").authenticated()
                        .requestMatchers(HttpMethod.GET,"/plan-versions/*/generation-jobs","/plan-generation-jobs/*").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":401,\"message\":\"请先提供有效的身份凭证\",\"data\":null}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":403,\"message\":\"无权执行此操作\",\"data\":null}");
                        }))
                .build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> { throw new UsernameNotFoundException("Login integration not configured"); };
    }
}
