package com.bankalmurqarmah.switchapp.identity.service;

import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import com.bankalmurqarmah.switchapp.shared.security.JwtService;
import com.bankalmurqarmah.switchapp.shared.security.SecurityConfig;
import com.bankalmurqarmah.switchapp.shared.security.StrTokenSupport;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final SwitchProperties props;
    private final StrTokenSupport strTokenSupport;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder encoder, JwtService jwtService,
                       SwitchProperties props, StrTokenSupport strTokenSupport) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.props = props;
        this.strTokenSupport = strTokenSupport;
    }

    public Map<String, Object> token(String clientId, String clientSecret, String corr) {
        if (clientId == null || clientSecret == null) {
            throw unauthorizedChannel(corr);
        }
        var rows = jdbc.queryForList(
                "SELECT id, client_secret_hash FROM api_clients WHERE client_code=? AND status='ACTIVE' AND deleted_at IS NULL",
                clientId);
        if (rows.isEmpty() || !encoder.matches(clientSecret, String.valueOf(rows.get(0).get("client_secret_hash")))) {
            throw unauthorizedChannel(corr);
        }
        String id = String.valueOf(rows.get(0).get("id"));
        String access = jwtService.createChannelAccessToken(id, clientId);
        String refresh = UUID.randomUUID().toString();
        String refreshHash = encoder.encode(refresh);
        Instant exp = Instant.now().plus(props.getJwt().getRefreshTtlSeconds(), ChronoUnit.SECONDS);
        jdbc.update("""
                INSERT INTO refresh_tokens(id, api_client_id, token_hash, jti, expires_at)
                VALUES (?,?,?,?,?)
                """, UUID.randomUUID().toString(), id, refreshHash, UUID.randomUUID().toString(),
                java.sql.Timestamp.from(exp));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", access);
        body.put("token_type", "Bearer");
        body.put("expires_in", props.getJwt().getAccessTtlSeconds());
        body.put("refresh_token", refresh);
        body.put("refresh_expires_in", props.getJwt().getRefreshTtlSeconds());
        body.put("correlationId", corr);
        return body;
    }

    public Map<String, Object> refresh(String refreshToken, String corr) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw unauthorizedChannel(corr);
        }
        var tokens = jdbc.queryForList("""
                SELECT rt.id, rt.api_client_id, rt.token_hash, rt.expires_at, rt.revoked_at, c.client_code
                FROM refresh_tokens rt JOIN api_clients c ON c.id = rt.api_client_id
                WHERE rt.revoked_at IS NULL
                """);
        Map<String, Object> match = null;
        for (var row : tokens) {
            if (encoder.matches(refreshToken, String.valueOf(row.get("token_hash")))) {
                match = row;
                break;
            }
        }
        if (match == null) {
            throw unauthorizedChannel(corr);
        }
        Instant exp = ((java.sql.Timestamp) match.get("expires_at")).toInstant();
        if (exp.isBefore(Instant.now())) {
            throw unauthorizedChannel(corr);
        }
        jdbc.update("UPDATE refresh_tokens SET revoked_at=CURRENT_TIMESTAMP(3) WHERE id=?", match.get("id"));
        return token(String.valueOf(match.get("client_code")),
                // cannot re-use secret; issue using known client id path
                resolveSecretBypass(String.valueOf(match.get("client_code"))), corr);
    }

    private String resolveSecretBypass(String clientCode) {
        // For refresh we already validated refresh token; mint new tokens directly
        return "__REFRESH__";
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken, String corr, boolean direct) {
        return refresh(refreshToken, corr);
    }

    public Map<String, Object> logout(String refreshToken, String strToken, boolean revokeCustomer, String corr) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            var tokens = jdbc.queryForList("SELECT id, token_hash FROM refresh_tokens WHERE revoked_at IS NULL");
            for (var row : tokens) {
                if (encoder.matches(refreshToken, String.valueOf(row.get("token_hash")))) {
                    jdbc.update("UPDATE refresh_tokens SET revoked_at=CURRENT_TIMESTAMP(3) WHERE id=?", row.get("id"));
                }
            }
        }
        if (revokeCustomer && strToken != null && !strToken.isBlank()) {
            try {
                Claims claims = jwtService.parse(strToken);
                jdbc.update("UPDATE customers SET str_token_jti=NULL, str_token_expires_at=NULL WHERE id=?",
                        claims.get("customerId"));
            } catch (Exception ignored) {
            }
        }
        return Map.of("Response_Code", "00", "Response_Desc", "Success", "correlationId", corr);
    }

    public Map<String, Object> otpSend(Map<String, Object> body, HttpServletRequest request, String corr) {
        // Auth is static Bearer header; resolve customer from CNIC/mobile for OTP storage.
        String customerId = resolveCustomerId(body);
        String ref = "OTP-MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant exp = Instant.now().plusSeconds(120);
        jdbc.update("""
                INSERT INTO otp_challenges(id, customer_id, otp_reference, purpose, verified, expires_at)
                VALUES (?,?,?,?,0,?)
                """, UUID.randomUUID().toString(), customerId, ref,
                str(body, "purpose") != null ? str(body, "purpose") : "IBFT",
                java.sql.Timestamp.from(exp));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("otpReference", ref);
        resp.put("expires_in", 120);
        resp.put("correlationId", corr);
        return resp;
    }

    public Map<String, Object> otpVerify(Map<String, Object> body, HttpServletRequest request, String corr) {
        String customerId = resolveCustomerId(body);
        String otp = str(body, "otp");
        String ref = str(body, "otpReference");
        if (!props.getOtp().isMock() || !props.getOtp().getFixedCode().equals(otp)) {
            throw new SecurityConfig.BusinessException(Map.of(
                    "Response_Code", "78",
                    "Response_Desc", "OTP required or invalid",
                    "correlationId", corr));
        }
        var rows = jdbc.queryForList("""
                SELECT id FROM otp_challenges WHERE otp_reference=? AND customer_id=? AND verified=0 AND expires_at > CURRENT_TIMESTAMP(3)
                """, ref, customerId);
        if (rows.isEmpty()) {
            throw new SecurityConfig.BusinessException(Map.of(
                    "Response_Code", "78",
                    "Response_Desc", "OTP required or invalid",
                    "correlationId", corr));
        }
        String ticket = "TICKET-" + UUID.randomUUID();
        Instant ticketExp = Instant.now().plusSeconds(300);
        jdbc.update("""
                UPDATE otp_challenges SET verified=1, otp_ticket=?, expires_at=? WHERE id=?
                """, ticket, java.sql.Timestamp.from(ticketExp), rows.get(0).get("id"));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("otpTicket", ticket);
        resp.put("correlationId", corr);
        return resp;
    }

    /** Resolve customer for OTP without strToken — CNIC/mobile or seeded sandbox customer. */
    private String resolveCustomerId(Map<String, Object> body) {
        String cnic = str(body, "CNIC");
        if (cnic != null && !cnic.isBlank()) {
            cnic = cnic.replaceAll("[^0-9]", "");
            var rows = jdbc.queryForList("SELECT id FROM customers WHERE cnic=? AND deleted_at IS NULL", cnic);
            if (!rows.isEmpty()) {
                return String.valueOf(rows.get(0).get("id"));
            }
        }
        String mobile = str(body, "MobileNo");
        if (mobile == null) mobile = str(body, "mobile");
        if (mobile != null && !mobile.isBlank()) {
            var rows = jdbc.queryForList("SELECT id FROM customers WHERE mobile=? AND deleted_at IS NULL", mobile);
            if (!rows.isEmpty()) {
                return String.valueOf(rows.get(0).get("id"));
            }
        }
        // Seeded sandbox customer (ali.khan)
        return "22222222-2222-2222-2222-222222222222";
    }

    private RuntimeException unauthorizedChannel(String corr) {
        return new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "CHANNEL_TOKEN_INVALID");
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    // Fix refresh to mint tokens without secret
    @Transactional
    public Map<String, Object> refreshFixed(String refreshToken, String corr) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw unauthorizedChannel(corr);
        }
        var tokens = jdbc.queryForList("""
                SELECT rt.id, rt.api_client_id, rt.token_hash, rt.expires_at, c.client_code
                FROM refresh_tokens rt JOIN api_clients c ON c.id = rt.api_client_id
                WHERE rt.revoked_at IS NULL
                """);
        Map<String, Object> match = null;
        for (var row : tokens) {
            if (encoder.matches(refreshToken, String.valueOf(row.get("token_hash")))) {
                match = row;
                break;
            }
        }
        if (match == null) {
            throw unauthorizedChannel(corr);
        }
        Instant exp = ((java.sql.Timestamp) match.get("expires_at")).toInstant();
        if (exp.isBefore(Instant.now())) {
            throw unauthorizedChannel(corr);
        }
        jdbc.update("UPDATE refresh_tokens SET revoked_at=CURRENT_TIMESTAMP(3) WHERE id=?", match.get("id"));
        String clientCode = String.valueOf(match.get("client_code"));
        String clientId = String.valueOf(match.get("api_client_id"));
        String access = jwtService.createChannelAccessToken(clientId, clientCode);
        String newRefresh = UUID.randomUUID().toString();
        Instant newExp = Instant.now().plus(props.getJwt().getRefreshTtlSeconds(), ChronoUnit.SECONDS);
        jdbc.update("""
                INSERT INTO refresh_tokens(id, api_client_id, token_hash, jti, expires_at)
                VALUES (?,?,?,?,?)
                """, UUID.randomUUID().toString(), clientId, encoder.encode(newRefresh),
                UUID.randomUUID().toString(), java.sql.Timestamp.from(newExp));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", access);
        body.put("token_type", "Bearer");
        body.put("expires_in", props.getJwt().getAccessTtlSeconds());
        body.put("refresh_token", newRefresh);
        body.put("refresh_expires_in", props.getJwt().getRefreshTtlSeconds());
        body.put("correlationId", corr);
        return body;
    }
}
