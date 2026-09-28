package com.bankalmurqarmah.switchapp.shared.security;

import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class ChannelJwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public ChannelJwtFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/token")
                || path.equals("/api/v1/auth/refresh")
                || path.equals("/api/v1/system/health")
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

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeUnauthorized(response, corr, "CHANNEL_TOKEN_MISSING");
            return;
        }
        try {
            Claims claims = jwtService.parse(auth.substring(7));
            if (!jwtService.isChannel(claims)) {
                writeUnauthorized(response, corr, "CHANNEL_TOKEN_INVALID");
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    claims,
                    List.of(new SimpleGrantedAuthority("ROLE_CHANNEL")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            writeUnauthorized(response, corr, "CHANNEL_TOKEN_EXPIRED");
        }
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
