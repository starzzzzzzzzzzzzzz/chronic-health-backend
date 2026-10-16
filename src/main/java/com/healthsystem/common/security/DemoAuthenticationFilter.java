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
    private final byte[] reviewerToken;
    public DemoAuthenticationFilter(String token) {this(token,null);}
    public DemoAuthenticationFilter(String token,String reviewerToken) {
        if (token == null || token.length() < 24) throw new IllegalStateException("Local demo token must contain at least 24 characters");
        this.token = token.getBytes(StandardCharsets.UTF_8);
        if(reviewerToken!=null && !reviewerToken.isBlank()) {
            if(reviewerToken.length()<24 || MessageDigest.isEqual(this.token,reviewerToken.getBytes(StandardCharsets.UTF_8)))throw new IllegalStateException("Local reviewer token must be distinct and contain at least 24 characters");
            this.reviewerToken=reviewerToken.getBytes(StandardCharsets.UTF_8);
        } else this.reviewerToken=null;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String supplied = request.getHeader("Authorization");
        String subject=null;
        if(supplied!=null) {
            byte[] provided=supplied.getBytes(StandardCharsets.UTF_8);
            if(MessageDigest.isEqual(token,provided))subject="demo-clinician";
            else if(reviewerToken!=null && MessageDigest.isEqual(reviewerToken,provided))subject="demo-reviewer";
        }
        if (subject!=null) {
            var principal = new BusinessPrincipal("demo-org", subject, "DEMO");
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
