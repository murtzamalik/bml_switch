package com.bankalmurqarmah.switchapp.shared.security;

import com.bankalmurqarmah.switchapp.adapter.imal.soap.ImalSoapClient;
import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Security + global error handlers with ERROR-level logs for bank-machine debugging.
 * Callers: Spring Boot. User: strong logging so errors catch without IDE.
 */
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
        private static final Logger log = LoggerFactory.getLogger(GlobalErrors.class);

        private final ObjectMapper objectMapper;

        public GlobalErrors(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @ExceptionHandler(StrTokenSupport.AuthBusinessException.class)
        public ResponseEntity<Map<String, Object>> auth(StrTokenSupport.AuthBusinessException ex, HttpServletRequest req) {
            Object corr = corr(req);
            log.error("AUTH_BUSINESS code={} desc={} path={} corr={}",
                    ex.getCode(), ex.getDesc(), req.getRequestURI(), corr);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Response_Code", ex.getCode());
            body.put("Response_Desc", ex.getDesc());
            body.put("correlationId", corr);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<Map<String, Object>> business(BusinessException ex, HttpServletRequest req) {
            Object corr = corr(req);
            Map<String, Object> body = new LinkedHashMap<>(ex.getBody());
            body.putIfAbsent("correlationId", corr);
            log.error("BUSINESS_ERROR status={} code={} desc={} failedStep={} path={} corr={} body={}",
                    ex.getStatus().value(),
                    body.get("Response_Code"),
                    body.get("Response_Desc"),
                    body.get("failedStep"),
                    req.getRequestURI(),
                    corr,
                    body);
            return ResponseEntity.status(ex.getStatus()).body(body);
        }

        @ExceptionHandler(ImalSoapClient.ImalSoapException.class)
        public ResponseEntity<Map<String, Object>> soap(ImalSoapClient.ImalSoapException ex, HttpServletRequest req) {
            Object corr = corr(req);
            log.error("SOAP_EXCEPTION op={} msg={} path={} corr={} responseSnippet={}",
                    ex.getOperation(), ex.getMessage(), req.getRequestURI(), corr,
                    ex.getResponseXml() != null && ex.getResponseXml().length() > 500
                            ? ex.getResponseXml().substring(0, 500) + "..."
                            : ex.getResponseXml(),
                    ex);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Response_Code", "96");
            body.put("Response_Desc", "iMal SOAP failed: " + ex.getMessage());
            body.put("failedStep", ex.getOperation());
            body.put("correlationId", corr);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
        }

        @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> validation(org.springframework.web.bind.MethodArgumentNotValidException ex, HttpServletRequest req) {
            Object corr = corr(req);
            log.error("VALIDATION_ERROR path={} corr={} fields={}", req.getRequestURI(), corr, ex.getBindingResult().getFieldErrors());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Response_Code", "30");
            body.put("Response_Desc", "VALIDATION_ERROR");
            body.put("correlationId", corr);
            body.put("fieldErrors", ex.getBindingResult().getFieldErrors().stream()
                    .map(fe -> Map.of("field", fe.getField(), "code", "INVALID", "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                    .toList());
            return ResponseEntity.badRequest().body(body);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> unhandled(Exception ex, HttpServletRequest req) {
            Object corr = corr(req);
            log.error("UNHANDLED path={} corr={} — {}", req.getRequestURI(), corr, ex.getMessage(), ex);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Response_Code", "96");
            body.put("Response_Desc", "Internal error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            body.put("correlationId", corr);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

        private static Object corr(HttpServletRequest req) {
            Object corr = req.getAttribute(CorrelationIds.HEADER);
            return corr != null ? corr : req.getHeader(CorrelationIds.HEADER);
        }
    }

    public static class BusinessException extends RuntimeException {
        private final Map<String, Object> body;
        private final HttpStatus status;

        public BusinessException(Map<String, Object> body) {
            this(body, HttpStatus.OK);
        }

        public BusinessException(Map<String, Object> body, HttpStatus status) {
            super(String.valueOf(body.get("Response_Desc")));
            this.body = body;
            this.status = status != null ? status : HttpStatus.OK;
        }

        public Map<String, Object> getBody() {
            return body;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }
}
