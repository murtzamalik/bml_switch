package com.bankalmurqarmah.switchapp.shared.security;

import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Existing filter (was JWT). Now validates static non-expiring Bearer token for all APIs.
 * Callers: SecurityFilterChain. Config: switch.auth.static-token / SWITCH_STATIC_TOKEN.
 * User instruction: logging strong enough for bank machine without IDE.
 */
@Component
public class ChannelJwtFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ChannelJwtFilter.class);

    private final SwitchProperties props;
    private final ObjectMapper objectMapper;

    public ChannelJwtFilter(SwitchProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/system/health")
                || path.startsWith("/actuator/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String corr = CorrelationIds.resolve(request);
        request.setAttribute(CorrelationIds.HEADER, corr);
        response.setHeader(CorrelationIds.HEADER, corr);
        MDC.put("correlationId", corr);

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            log.error("AUTH FAIL STATIC_TOKEN_MISSING path={} corr={}", request.getRequestURI(), corr);
            writeUnauthorized(response, corr, "STATIC_TOKEN_MISSING");
            return;
        }
        String presented = auth.substring(7).trim();
        String expected = props.getAuth().getStaticToken();
        if (expected == null || expected.isBlank() || !constantTimeEquals(presented, expected)) {
            log.error("AUTH FAIL STATIC_TOKEN_INVALID path={} corr={}", request.getRequestURI(), corr);
            writeUnauthorized(response, corr, "STATIC_TOKEN_INVALID");
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                "static-channel",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CHANNEL")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private void writeUnauthorized(HttpServletResponse response, String corr, String detail) throws IOException {
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "type", "about:blank",
                "title", "Unauthorized",
                "status", 401,
                "detail", detail,
                "correlationId", corr
        ));
    }
}
