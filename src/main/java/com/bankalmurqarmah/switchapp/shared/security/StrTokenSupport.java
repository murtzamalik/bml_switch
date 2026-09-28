package com.bankalmurqarmah.switchapp.shared.security;

import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StrTokenSupport {
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public StrTokenSupport(JwtService jwtService, ObjectMapper objectMapper, SwitchProperties props) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    public Claims requireCustomer(HttpServletRequest request, MapLike body) {
        String token = body != null ? body.strToken() : null;
        if (token == null || token.isBlank()) {
            token = request.getHeader("X-Str-Token");
            if (token == null || token.isBlank()) {
                token = request.getHeader("X-Customer-Token");
            }
        }
        if (token == null || token.isBlank()) {
            throw new AuthBusinessException("91", "SESSION_EXPIRED");
        }
        try {
            Claims claims = jwtService.parse(token);
            if (!jwtService.isCustomer(claims)) {
                throw new AuthBusinessException("91", "SESSION_EXPIRED");
            }
            return claims;
        } catch (AuthBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthBusinessException("91", "SESSION_EXPIRED");
        }
    }

    public Claims requireCustomerFromJson(HttpServletRequest request, String jsonBody) {
        final String[] tokenHolder = {null};
        try {
            if (jsonBody != null && !jsonBody.isBlank()) {
                JsonNode node = objectMapper.readTree(jsonBody);
                if (node.hasNonNull("strToken")) {
                    tokenHolder[0] = node.get("strToken").asText();
                }
            }
        } catch (Exception ignored) {
        }
        return requireCustomer(request, () -> tokenHolder[0]);
    }

    @FunctionalInterface
    public interface MapLike {
        String strToken();
    }

    public static class AuthBusinessException extends RuntimeException {
        private final String code;
        private final String desc;

        public AuthBusinessException(String code, String desc) {
            super(desc);
            this.code = code;
            this.desc = desc;
        }

        public String getCode() { return code; }
        public String getDesc() { return desc; }
    }
}
