package com.bankalmurqarmah.switchapp.identity.web;

import com.bankalmurqarmah.switchapp.identity.service.AuthService;
import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/token")
    public Map<String, Object> token(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String corr = CorrelationIds.resolve(request);
        return authService.token(
                str(body, "client_id"),
                str(body, "client_secret"),
                corr);
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return authService.refreshFixed(str(body, "refresh_token"), CorrelationIds.resolve(request));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        if (body == null) body = Map.of();
        return authService.logout(str(body, "refresh_token"), str(body, "strToken"),
                Boolean.TRUE.equals(body.get("revokeCustomerSession")), CorrelationIds.resolve(request));
    }

    @PostMapping("/otp/send")
    public Map<String, Object> otpSend(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return authService.otpSend(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/otp/verify")
    public Map<String, Object> otpVerify(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return authService.otpVerify(body, request, CorrelationIds.resolve(request));
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
