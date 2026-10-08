package com.bankalmurqarmah.switchapp.shared.web;

import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Logs every API call with correlationId, status, duration.
 * On 4xx/5xx also dumps request body so bank-machine ops can debug without IDE.
 * Callers: Spring filter chain (highest precedence). Used by all /api/v1/**.
 * User: logging strong enough to catch errors immediately on bank machine without Cursor/IDE.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiAccessLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiAccessLoggingFilter.class);
    private static final int MAX_BODY = 16_384;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
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

        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request, MAX_BODY);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(req, res);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            log.error("API UNHANDLED {} {} corr={} ({}ms) — {}",
                    request.getMethod(), request.getRequestURI(), corr, ms, e.getMessage(), e);
            throw e;
        } finally {
            long ms = System.currentTimeMillis() - start;
            int status = res.getStatus();
            String path = request.getRequestURI();
            if (status >= 400) {
                String body = truncate(new String(req.getContentAsByteArray(), StandardCharsets.UTF_8));
                log.error("API FAIL {} {} status={} corr={} ({}ms) body={}",
                        request.getMethod(), path, status, corr, ms, body.isBlank() ? "-" : body);
            } else if (!path.equals("/api/v1/system/health")) {
                log.info("API OK {} {} status={} corr={} ({}ms)",
                        request.getMethod(), path, status, corr, ms);
            }
            res.copyBodyToResponse();
            MDC.remove("correlationId");
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= MAX_BODY ? t : t.substring(0, MAX_BODY) + "...(truncated)";
    }
}
