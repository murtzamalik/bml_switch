package com.bankalmurqarmah.switchapp.accounts.service;

import com.bankalmurqarmah.switchapp.adapter.imal.port.ImalPort;
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

import java.util.*;

@Service
public class AccountService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final ImalPort imalPort;
    private final StrTokenSupport strTokenSupport;
    private final SwitchProperties props;

    public AccountService(JdbcTemplate jdbc, PasswordEncoder encoder, JwtService jwtService, ImalPort imalPort,
                          StrTokenSupport strTokenSupport, SwitchProperties props) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.imalPort = imalPort;
        this.strTokenSupport = strTokenSupport;
        this.props = props;
    }

    public Map<String, Object> login(Map<String, Object> body, String corr) {
        String username = str(body, "UserName");
        String cnic = normalizeCnic(str(body, "CNIC"));
        String password = str(body, "password");
        if (password == null) password = str(body, "Password");
        List<Map<String, Object>> rows;
        if (username != null && !username.isBlank()) {
            rows = jdbc.queryForList("SELECT * FROM customers WHERE username=? AND deleted_at IS NULL", username);
        } else {
            rows = jdbc.queryForList("SELECT * FROM customers WHERE cnic=? AND deleted_at IS NULL", cnic);
        }
        if (rows.isEmpty()) {
            return Map.of("Response_Code", "14", "Response_Desc", "Invalid credentials", "correlationId", corr);
        }
        var cust = rows.get(0);
        if (cust.get("locked_until") != null && ((java.sql.Timestamp) cust.get("locked_until")).toInstant().isAfter(java.time.Instant.now())) {
            throw new StrTokenSupport.AuthBusinessException("75", "LOCKOUT");
        }
        if (!encoder.matches(password != null ? password : "", String.valueOf(cust.get("password_hash")))) {
            int fails = ((Number) cust.get("failed_login_count")).intValue() + 1;
            if (fails >= 5) {
                jdbc.update("UPDATE customers SET failed_login_count=?, locked_until=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 MINUTE) WHERE id=?",
                        fails, cust.get("id"));
                throw new StrTokenSupport.AuthBusinessException("75", "LOCKOUT");
            }
            jdbc.update("UPDATE customers SET failed_login_count=? WHERE id=?", fails, cust.get("id"));
            return Map.of("Response_Code", "14", "Response_Desc", "Invalid credentials", "correlationId", corr);
        }
        jdbc.update("UPDATE customers SET failed_login_count=0, locked_until=NULL WHERE id=?", cust.get("id"));
        String token = jwtService.createCustomerToken(String.valueOf(cust.get("id")),
                String.valueOf(cust.get("cnic")), String.valueOf(cust.get("username")));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("strToken", token);
        resp.put("UserName", cust.get("username"));
        resp.put("correlationId", corr);
        return resp;
    }

    public Map<String, Object> register(Map<String, Object> body, String corr) {
        String cnic = normalizeCnic(str(body, "CNIC"));
        String accountNo = str(body, "AccountNo");
        String mobile = normalizeMobile(str(body, "MobileNo"));
        String username = str(body, "UserName");
        String password = str(body, "password");
        if (password == null) password = "Sandbox@123";
        var acct = jdbc.queryForList("SELECT id, customer_id FROM accounts WHERE account_number=?", accountNo);
        if (acct.isEmpty()) {
            return Map.of("Response_Code", "14", "Response_Desc", "Account not found", "correlationId", corr);
        }
        String customerId = String.valueOf(acct.get(0).get("customer_id"));
        jdbc.update("""
                UPDATE customers SET username=?, password_hash=?, mobile=?, cnic=COALESCE(cnic, ?) WHERE id=?
                """, username, encoder.encode(password), mobile, cnic, customerId);
        return Map.of("Response_Code", "00", "Response_Desc", "Success", "correlationId", corr);
    }

    public Map<String, Object> customerDetail(Map<String, Object> body, HttpServletRequest request, String corr) {
        strTokenSupport.requireCustomer(request, () -> str(body, "strToken"));
        String cnic = normalizeCnic(str(body, "CNIC"));
        Map<String, Object> detail = imalPort.customerDetail(cnic);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response", Map.of(
                "CNIC", detail.get("CNIC"),
                "FULL_NAME", detail.get("FULL_NAME"),
                "Email", detail.get("Email"),
                "ADDRESS", detail.get("ADDRESS"),
                "MOBILE_NUM", detail.get("MOBILE_NUM"),
                "D_Birth", detail.get("D_Birth")));
        resp.put("Response_Code", "00".equals(detail.get("code")) ? "00" : "14");
        resp.put("Response_Desc", "00".equals(detail.get("code")) ? "Success" : "Not found");
        resp.put("correlationId", corr);
        return resp;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> accountList(Map<String, Object> body, HttpServletRequest request, String corr) {
        strTokenSupport.requireCustomer(request, () -> str(body, "strToken"));
        String cnic = null;
        Object req = body.get("Request");
        if (req instanceof Map<?, ?> m) {
            cnic = normalizeCnic(String.valueOf(m.get("CNIC")));
        }
        if (cnic == null) cnic = normalizeCnic(str(body, "CNIC"));
        Map<String, Object> list = imalPort.listAccountsByCnic(cnic);
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) list.get("accounts");
        List<Map<String, Object>> casa = new ArrayList<>();
        for (var a : accounts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("Account_Number", a.get("account_number"));
            item.put("Account_IBAN", a.get("iban"));
            item.put("Account_Title", a.get("account_title"));
            item.put("Account_Type", a.get("account_type"));
            item.put("Account_Currency", a.get("currency"));
            item.put("Account_Status", a.get("status"));
            item.put("Account_Description", "");
            item.put("ACCT_STATUS_CODE", "ACTIVE".equals(a.get("status")) ? "A" : "C");
            casa.add(item);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response", Map.of("CASA_Account_List", casa));
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("correlationId", corr);
        return resp;
    }

    @Transactional
    public Map<String, Object> open(Map<String, Object> body, String idem, HttpServletRequest request, String corr) {
        Claims claims = strTokenSupport.requireCustomer(request, () -> str(body, "strToken"));
        String cnic = normalizeCnic(str(body, "CNIC"));
        if (cnic == null) cnic = String.valueOf(claims.get("cnic"));
        // odd CNIC last digit -> manual review unless approved
        char last = cnic.charAt(cnic.length() - 1);
        boolean odd = (last - '0') % 2 == 1;
        if (odd) {
            Integer approved = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM onboarding_applications WHERE cnic=? AND status='APPROVED'", Integer.class, cnic);
            if (approved == null || approved == 0) {
                throw new SecurityConfig.BusinessException(Map.of(
                        "Response_Code", "79",
                        "Response_Desc", "Manual review pending",
                        "correlationId", corr));
            }
        }
        String fullName = str(body, "fullName") != null ? str(body, "fullName") : "CUSTOMER";
        String mobile = normalizeMobile(str(body, "MobileNo"));
        String product = str(body, "productCode") != null ? str(body, "productCode") : "ASAAN_DIGITAL";
        String accountNumber = imalPort.openAccount(cnic, fullName, mobile, product);
        var acct = jdbc.queryForList("SELECT account_number, iban, account_title, currency, product_code, status FROM accounts WHERE account_number=?", accountNumber).get(0);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("correlationId", corr);
        resp.put("accountNumber", acct.get("account_number"));
        resp.put("IBAN", acct.get("iban"));
        resp.put("accountTitle", acct.get("account_title"));
        resp.put("currency", acct.get("currency"));
        resp.put("productCode", acct.get("product_code"));
        resp.put("status", acct.get("status"));
        return resp;
    }

    public Map<String, Object> uploadUnikrew(Map<String, Object> body, HttpServletRequest request, String corr) {
        strTokenSupport.requireCustomer(request, () -> str(body, "strToken"));
        String cnic = normalizeCnic(str(body, "CNIC"));
        if (cnic == null) cnic = "4210112345678";
        boolean even = (cnic.charAt(cnic.length() - 1) - '0') % 2 == 0;
        double confidence = even ? 0.95 : 0.45;
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("isSmartNic", true);
        resp.put("ocrResponse", Map.of(
                "uniqueIdentifier", cnic,
                "fields", List.of(
                        Map.of("name", "name", "value", "ALI KHAN"),
                        Map.of("name", "cnic", "value", cnic),
                        Map.of("name", "dob", "value", "1990-01-01")),
                "rawData", "",
                "transliteratedText", null,
                "isNicSame", true));
        resp.put("faceComparisonResponse", Map.of(
                "thresholds", Map.of("low", 0.30, "mid", 0.60, "high", 0.80),
                "confidence", confidence));
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("correlationId", corr);
        upsertOnboarding(cnic, even ? "DOCS_UPLOADED" : "MANUAL_REVIEW", even ? 4 : 5, even ? 2 : 1);
        return resp;
    }

    public Map<String, Object> validateUnikrew(Map<String, Object> body, HttpServletRequest request, String corr) {
        strTokenSupport.requireCustomer(request, () -> str(body, "strToken"));
        return Map.of(
                "cnicDetails", Map.of("side", "front", "type", "smart"),
                "details", Map.of("isBlurry", false, "isTooBright", false, "isTooDark", false),
                "isAuthentic", true,
                "Response_Code", "00",
                "Response_Desc", "Success",
                "correlationId", corr);
    }

    public Map<String, Object> liveliness(Map<String, Object> body, HttpServletRequest request, String corr) {
        strTokenSupport.requireCustomer(request, () -> str(body, "strToken"));
        return Map.of(
                "StatusCode", "200",
                "StatusMessage", "Success",
                "ResponseObject", Map.of(
                        "probability", "0.97",
                        "score", "95",
                        "quality", "good",
                        "error", "",
                        "error_code", ""),
                "Response_Code", "00",
                "Response_Desc", "Success",
                "correlationId", corr);
    }

    public Map<String, Object> fingers(Map<String, Object> body, HttpServletRequest request, String corr) {
        strTokenSupport.requireCustomer(request, () -> str(body, "strToken"));
        String cnic = normalizeCnic(str(body, "CNIC"));
        if (cnic == null) cnic = normalizeCnic(str(body, "citizenNumber"));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Status_Code", "100");
        resp.put("Message", "successful");
        resp.put("Session_ID", "3712100001813731502");
        resp.put("Citizen_Number", cnic);
        resp.put("FINGER_ONE", "M");
        resp.put("FINGER_TWO", "M");
        resp.put("FINGER_THREE", "M");
        resp.put("FINGER_FOUR", "M");
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("correlationId", corr);
        return resp;
    }

    public Map<String, Object> approveMock(Map<String, Object> body, String corr) {
        if (!props.getProfile().isSandbox()) {
            return Map.of("Response_Code", "93", "Response_Desc", "Forbidden", "correlationId", corr);
        }
        String cnic = normalizeCnic(str(body, "CNIC"));
        upsertOnboarding(cnic, "APPROVED", 4, 2);
        return Map.of("Response_Code", "00", "Response_Desc", "Success", "correlationId", corr, "status", "APPROVED");
    }

    public Map<String, Object> changePassword(Map<String, Object> body, HttpServletRequest request, String corr) {
        Claims claims = strTokenSupport.requireCustomer(request, () -> str(body, "strToken"));
        String current = str(body, "currentPassword");
        String next = str(body, "newPassword");
        var rows = jdbc.queryForList("SELECT password_hash FROM customers WHERE id=?", claims.get("customerId"));
        if (rows.isEmpty() || !encoder.matches(current, String.valueOf(rows.get(0).get("password_hash")))) {
            return Map.of("Response_Code", "14", "Response_Desc", "Invalid credentials", "correlationId", corr);
        }
        jdbc.update("UPDATE customers SET password_hash=? WHERE id=?", encoder.encode(next), claims.get("customerId"));
        return Map.of("Response_Code", "00", "Response_Desc", "Success", "correlationId", corr);
    }

    public Map<String, Object> resetPasswordMock(Map<String, Object> body, String corr) {
        if (!props.getProfile().isSandbox()) {
            return Map.of("Response_Code", "93", "Response_Desc", "Forbidden", "correlationId", corr);
        }
        String cnic = normalizeCnic(str(body, "CNIC"));
        String next = str(body, "newPassword");
        jdbc.update("UPDATE customers SET password_hash=?, failed_login_count=0, locked_until=NULL WHERE cnic=?",
                encoder.encode(next), cnic);
        return Map.of("Response_Code", "00", "Response_Desc", "Success", "correlationId", corr);
    }

    public Map<String, Object> information(Map<String, Object> body, HttpServletRequest request, String corr) {
        strTokenSupport.requireCustomer(request, () -> str(body, "strToken"));
        String acct = str(body, "fromAccount");
        if (acct == null) acct = str(body, "AccountNo");
        var rows = jdbc.queryForList(
                "SELECT account_number, iban, account_title, account_type, product_code, currency, status, branch_code FROM accounts WHERE account_number=?",
                acct);
        if (rows.isEmpty()) {
            return Map.of("Response_Code", "14", "Response_Desc", "Account not found", "correlationId", corr);
        }
        Map<String, Object> resp = new LinkedHashMap<>(rows.get(0));
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("correlationId", corr);
        return resp;
    }

    private void upsertOnboarding(String cnic, String status, int stepId, int statusId) {
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM onboarding_applications WHERE cnic=?", Integer.class, cnic);
        if (cnt != null && cnt > 0) {
            jdbc.update("UPDATE onboarding_applications SET status=?, step_id=?, status_id=? WHERE cnic=?",
                    status, stepId, statusId, cnic);
        } else {
            jdbc.update("""
                    INSERT INTO onboarding_applications(id, cnic, status, step_id, status_id) VALUES (?,?,?,?,?)
                    """, UUID.randomUUID().toString(), cnic, status, stepId, statusId);
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String normalizeCnic(String cnic) {
        if (cnic == null || "null".equals(cnic)) return null;
        return cnic.replaceAll("[^0-9]", "");
    }

    private static String normalizeMobile(String mobile) {
        if (mobile == null) return null;
        String digits = mobile.replaceAll("[^0-9]", "");
        if (digits.startsWith("92") && digits.length() == 12) {
            return "0" + digits.substring(2);
        }
        return digits;
    }
}
