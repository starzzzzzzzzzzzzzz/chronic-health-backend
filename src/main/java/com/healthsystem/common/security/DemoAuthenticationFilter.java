package com.healthsystem.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Explicit local development identity; never registered as a servlet filter bean. */
public final class DemoAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] token;
    public DemoAuthenticationFilter(String token) {
        if (token == null || token.length() < 24) throw new IllegalStateException("Local demo token must contain at least 24 characters");
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String supplied = request.getHeader("Authorization");
        if (supplied != null && MessageDigest.isEqual(token, supplied.getBytes(StandardCharsets.UTF_8))) {
            var principal = new BusinessPrincipal("demo-org", "demo-clinician", "DEMO");
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
