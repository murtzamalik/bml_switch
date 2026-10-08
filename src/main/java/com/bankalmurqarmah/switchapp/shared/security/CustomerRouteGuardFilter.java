package com.bankalmurqarmah.switchapp.shared.security;

import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Existing filter. Spring Security filter chain. CHANNEL_ONLY now includes open + account-list.
 * Instruction: Implement the iMal Account Open + Existing Registration Plan.
 */
@Component
public class CustomerRouteGuardFilter extends OncePerRequestFilter {
    private static final Set<String> CHANNEL_ONLY = Set.of(
            "/api/v1/auth/logout",
            "/api/v1/account/open",
            "/api/v1/account/account-list",
            "/api/v1/system/version"
    );

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public CustomerRouteGuardFilter(JwtService jwtService, ObjectMapper objectMapper, PasswordEncoder passwordEncoder, SwitchProperties props) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) return true;
        if (path.startsWith("/api/v1/lookups/")) return true;
        if (path.startsWith("/api/v1/auth/token") || path.startsWith("/api/v1/auth/refresh")) return true;
        if (path.equals("/api/v1/system/health")) return true;
        if (CHANNEL_ONLY.contains(path)) return true;
        if (path.startsWith("/api/v1/auth/otp/")) return false;
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);
    }

    public void writeSessionExpired(HttpServletResponse response, String corr) throws IOException {
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "Response_Code", "91",
                "Response_Desc", "SESSION_EXPIRED",
                "correlationId", corr != null ? corr : CorrelationIds.HEADER
        ));
    }

    public JwtService jwtService() {
        return jwtService;
    }
}
