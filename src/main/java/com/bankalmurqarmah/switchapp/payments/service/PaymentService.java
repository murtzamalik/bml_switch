package com.bankalmurqarmah.switchapp.payments.service;

import com.bankalmurqarmah.switchapp.adapter.imal.port.ImalPort;
import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import com.bankalmurqarmah.switchapp.shared.security.SecurityConfig;
import com.bankalmurqarmah.switchapp.shared.security.StrTokenSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {
    private final ImalPort imalPort;
    private final StrTokenSupport strTokenSupport;
    private final JdbcTemplate jdbc;
    private final SwitchProperties props;
    private final ObjectMapper objectMapper;

    public PaymentService(ImalPort imalPort, StrTokenSupport strTokenSupport, JdbcTemplate jdbc,
                          SwitchProperties props, ObjectMapper objectMapper) {
        this.imalPort = imalPort;
        this.strTokenSupport = strTokenSupport;
        this.jdbc = jdbc;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> ift(Map<String, Object> body, String idem, HttpServletRequest request, String corr) {
        strTokenSupport.requireCustomer(request, () -> str(body, "strToken"));
        if (idem == null || idem.isBlank()) {
            throw new SecurityConfig.BusinessException(Map.of(
                    "Response_Code", "30", "Response_Desc", "Idempotency-Key required", "correlationId", corr));
        }
        Map<String, Object> cached = loadIdempotent(idem);
        if (cached != null) return cached;

        String stan = str(body, "stan");
        if (stan == null || stan.isBlank()) stan = str(body, "intSTAN");
        if (stan == null || stan.isBlank()) stan = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));

        BigDecimal amount = parseAmount(str(body, "amount"));
        var result = imalPort.iftPayment(
                str(body, "fromAccount"),
                str(body, "toAccount"),
                amount,
                stan,
                str(body, "PurposeOfPayment"),
                str(body, "Description"),
                idem);

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("agentAccount", props.getBank().getAgentAccount());
        nested.put("amount", amount.toPlainString());
        nested.put("channelId", props.getChannelId());
        nested.put("fromAccount", str(body, "fromAccount"));
        nested.put("password", "");
        nested.put("processingCode", "");
        nested.put("stan", result.stan());
        nested.put("status", result.responseCode());
        nested.put("statusDescription", result.responseDesc());
        nested.put("toAccount", str(body, "toAccount"));
        nested.put("userId", "");
        nested.put("PurposeOfPayment", str(body, "PurposeOfPayment"));
        nested.put("TransmissionDateTime", "");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("return", nested);
        resp.put("Response_Code", result.responseCode());
        resp.put("Response_Desc", result.responseDesc());
        resp.put("correlationId", corr);
        saveIdempotent(idem, resp);
        return resp;
    }

    @Transactional
    public Map<String, Object> ibft(Map<String, Object> body, String idem, HttpServletRequest request, String corr) {
        strTokenSupport.requireCustomer(request, () -> str(body, "strToken"));
        if (idem == null || idem.isBlank()) {
            throw new SecurityConfig.BusinessException(Map.of(
                    "Response_Code", "30", "Response_Desc", "Idempotency-Key required", "correlationId", corr));
        }
        Map<String, Object> cached = loadIdempotent(idem);
        if (cached != null) return cached;

        String otpTicket = str(body, "otpTicket");
        if (otpTicket == null || otpTicket.isBlank()) {
            throw new SecurityConfig.BusinessException(Map.of(
                    "Response_Code", "78", "Response_Desc", "OTP required or invalid", "correlationId", corr));
        }
        Integer ok = jdbc.queryForObject(
                "SELECT COUNT(*) FROM otp_challenges WHERE otp_ticket=? AND verified=1 AND expires_at > CURRENT_TIMESTAMP(3)",
                Integer.class, otpTicket);
        if (ok == null || ok == 0) {
            throw new SecurityConfig.BusinessException(Map.of(
                    "Response_Code", "78", "Response_Desc", "OTP required or invalid", "correlationId", corr));
        }

        String stan = str(body, "stan");
        if (stan == null || stan.isBlank()) stan = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));
        BigDecimal amount = parseAmount(str(body, "amount"));
        var result = imalPort.ibftPayment(
                str(body, "fromAccount"),
                str(body, "toIBAN"),
                str(body, "toIMD"),
                str(body, "toAccount"),
                amount,
                stan,
                str(body, "PurposeOfPayment"),
                str(body, "Description"),
                idem);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", result.responseCode());
        resp.put("Response_Desc", result.responseDesc());
        resp.put("correlationId", corr);
        resp.put("stan", result.stan());
        resp.put("transactionID", result.transactionId());
        resp.put("fee", "0.00");
        saveIdempotent(idem, resp);
        return resp;
    }

    private Map<String, Object> loadIdempotent(String key) {
        var rows = jdbc.queryForList("SELECT response_body FROM idempotency_keys WHERE idempotency_key=? AND expires_at > CURRENT_TIMESTAMP(3)", key);
        if (rows.isEmpty()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(String.valueOf(rows.get(0).get("response_body")), Map.class);
            return body;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveIdempotent(String key, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            jdbc.update("""
                    INSERT INTO idempotency_keys(id, idempotency_key, response_body, http_status, status, expires_at)
                    VALUES (?,?,?,?, 'COMPLETED', ?)
                    ON DUPLICATE KEY UPDATE response_body=VALUES(response_body)
                    """, UUID.randomUUID().toString(), key, json, 200,
                    java.sql.Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)));
        } catch (Exception ignored) {
        }
    }

    private static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            throw new SecurityConfig.BusinessException(Map.of("Response_Code", "30", "Response_Desc", "VALIDATION_ERROR"));
        }
        String cleaned = amount.replace(",", "").trim();
        return new BigDecimal(cleaned);
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
