package com.bankalmurqarmah.switchapp.shared.security;

import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ChannelJwtFilter channelJwtFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/token",
                                "/api/v1/auth/refresh",
                                "/api/v1/system/health",
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(channelJwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @RestControllerAdvice
    public static class GlobalErrors {
        private final ObjectMapper objectMapper;

        public GlobalErrors(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @ExceptionHandler(StrTokenSupport.AuthBusinessException.class)
        public ResponseEntity<Map<String, Object>> auth(StrTokenSupport.AuthBusinessException ex, HttpServletRequest req) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Response_Code", ex.getCode());
            body.put("Response_Desc", ex.getDesc());
            Object corr = req.getAttribute(CorrelationIds.HEADER);
            body.put("correlationId", corr != null ? corr : req.getHeader(CorrelationIds.HEADER));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<Map<String, Object>> business(BusinessException ex, HttpServletRequest req) {
            Map<String, Object> body = new LinkedHashMap<>(ex.getBody());
            Object corr = req.getAttribute(CorrelationIds.HEADER);
            body.putIfAbsent("correlationId", corr != null ? corr : req.getHeader(CorrelationIds.HEADER));
            return ResponseEntity.ok(body);
        }

        @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> validation(org.springframework.web.bind.MethodArgumentNotValidException ex, HttpServletRequest req) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Response_Code", "30");
            body.put("Response_Desc", "VALIDATION_ERROR");
            Object corr = req.getAttribute(CorrelationIds.HEADER);
            body.put("correlationId", corr != null ? corr : req.getHeader(CorrelationIds.HEADER));
            body.put("fieldErrors", ex.getBindingResult().getFieldErrors().stream()
                    .map(fe -> Map.of("field", fe.getField(), "code", "INVALID", "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                    .toList());
            return ResponseEntity.badRequest().body(body);
        }
    }

    public static class BusinessException extends RuntimeException {
        private final Map<String, Object> body;

        public BusinessException(Map<String, Object> body) {
            super(String.valueOf(body.get("Response_Desc")));
            this.body = body;
        }

        public Map<String, Object> getBody() {
            return body;
        }
    }
}
