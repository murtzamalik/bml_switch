package com.bankalmurqarmah.switchapp.accounts.service;

import com.bankalmurqarmah.switchapp.adapter.imal.mock.MockImalAdapter;
import com.bankalmurqarmah.switchapp.adapter.imal.port.ImalPort;
import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import com.bankalmurqarmah.switchapp.shared.security.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Existing service rewritten per plan.
 * Callers: AccountController. APIs: POST /account/open, /account/account-list.
 * User instruction: strong logging on bank machine without IDE.
 */
@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final ImalPort imalPort;
    private final SwitchProperties props;

    public AccountService(ImalPort imalPort, SwitchProperties props) {
        this.imalPort = imalPort;
        this.props = props;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> accountList(Map<String, Object> body, String corr) {
        String cnic = resolveCnic(body);
        if (cnic == null || cnic.isBlank()) {
            throw fail(HttpStatus.BAD_REQUEST, "30", "CNIC is required", "account-list", corr);
        }
        Map<String, Object> list = imalPort.listAccountsByCnic(cnic);
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) list.getOrDefault("accounts", List.of());
        log.info("account-list cnic={} count={} corr={}", cnic, accounts.size(), corr);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("correlationId", corr);
        resp.put("accounts", accounts);
        return resp;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> open(Map<String, Object> body, String corr) {
        String cnic = normalizeCnic(firstNonBlank(str(body, "CNIC"), str(body, "idNumber")));
        String productCode = str(body, "productCode");
        String accGl = str(body, "accGl");
        log.info("account/open START cnic={} product={} accGl={} mockImal={} corr={}",
                cnic, productCode, accGl, props.getImal().isMock(), corr);
        if (cnic == null || cnic.isBlank()) {
            throw fail(HttpStatus.BAD_REQUEST, "30", "CNIC is required", "createRetailCif", corr);
        }
        if (productCode == null || productCode.isBlank()) {
            throw fail(HttpStatus.BAD_REQUEST, "30", "productCode is required", "createGeneralAccount", corr);
        }
        if (accGl == null || accGl.isBlank()) {
            throw fail(HttpStatus.BAD_REQUEST, "30", "accGl is required", "createGeneralAccount", corr);
        }

        Map<String, Object> cifDetails = buildCifDetails(body, cnic);
        ImalPort.CifResult cif = imalPort.createRetailCif(cifDetails);
        if (!"0".equals(cif.statusCode())) {
            log.error("account/open FAIL createRetailCif code={} desc={} corr={}",
                    cif.statusCode(), cif.statusDesc(), corr);
            throw fail(HttpStatus.BAD_GATEWAY, "96",
                    "CIF create failed: " + cif.statusDesc(), "createRetailCif", corr);
        }
        String cifNo = cif.cifNo();
        log.info("account/open STEP createRetailCif OK cifNo={} created={} corr={}", cifNo, cif.created(), corr);

        ImalPort.StatusResult validated = imalPort.validateRetailCif(cifNo);
        if (!"0".equals(validated.statusCode())) {
            log.error("account/open FAIL validateRetailCif cifNo={} code={} desc={} corr={}",
                    cifNo, validated.statusCode(), validated.statusDesc(), corr);
            throw fail(HttpStatus.UNPROCESSABLE_ENTITY, "96",
                    "CIF validation failed: " + validated.statusDesc(), "validateRetailCif", corr);
        }
        log.info("account/open STEP validateRetailCif OK cifNo={} corr={}", cifNo, corr);

        Map<String, Object> listResult = imalPort.listAccountsByCif(cifNo);
        List<Map<String, Object>> accounts = new ArrayList<>(
                (List<Map<String, Object>>) listResult.getOrDefault("accounts", List.of()));

        boolean accountCreated = false;
        if (!hasMatchingProduct(accounts, productCode, accGl)) {
            Map<String, Object> accountDetails = buildAccountDetails(body);
            ImalPort.AccountCreateResult created = imalPort.createGeneralAccount(cifNo, accGl, productCode, accountDetails);
            if (!"0".equals(created.statusCode())) {
                log.error("account/open FAIL createGeneralAccount cifNo={} code={} desc={} corr={}",
                        cifNo, created.statusCode(), created.statusDesc(), corr);
                throw fail(HttpStatus.BAD_GATEWAY, "96",
                        "Account create failed: " + created.statusDesc(), "createGeneralAccount", corr);
            }
            log.info("account/open STEP createGeneralAccount OK additionalRef={} corr={}",
                    created.additionalRef(), corr);

            if (Boolean.TRUE.equals(body.get("simulateAuthorizeFail"))) {
                MockImalAdapter.FORCE_AUTHORIZE_FAIL.set(true);
            }
            ImalPort.StatusResult auth = imalPort.authorizeGeneralAccount(created.additionalRef());
            if (!"0".equals(auth.statusCode())) {
                log.error("account/open FAIL authorizeGeneralAccount ref={} code={} desc={} corr={}",
                        created.additionalRef(), auth.statusCode(), auth.statusDesc(), corr);
                throw fail(HttpStatus.UNPROCESSABLE_ENTITY, "96",
                        "cannot open account, authorization failed", "authorizeGeneralAccount", corr);
            }
            log.info("account/open STEP authorizeGeneralAccount OK ref={} corr={}",
                    created.additionalRef(), corr);
            accountCreated = true;
            listResult = imalPort.listAccountsByCif(cifNo);
            accounts = new ArrayList<>(
                    (List<Map<String, Object>>) listResult.getOrDefault("accounts", List.of()));
        } else {
            log.info("account/open Option P — product already present cifNo={} product={} corr={}",
                    cifNo, productCode, corr);
        }

        log.info("account/open DONE cifNo={} cifCreated={} accountCreated={} accounts={} corr={}",
                cifNo, cif.created(), accountCreated, accounts.size(), corr);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("correlationId", corr);
        resp.put("cifNo", cifNo);
        resp.put("cifCreated", cif.created());
        resp.put("accountCreated", accountCreated);
        resp.put("accounts", accounts);
        return resp;
    }

    private Map<String, Object> buildCifDetails(Map<String, Object> body, String cnic) {
        Map<String, Object> cif = new LinkedHashMap<>();
        cif.put("idNumber", cnic);
        cif.put("fullName", firstNonBlank(str(body, "fullName"), "CUSTOMER"));
        cif.put("firstName", str(body, "firstName"));
        cif.put("lastName", str(body, "lastName"));
        cif.put("dateOfBirth", str(body, "dateOfBirth"));
        cif.put("idDeliveryDate", str(body, "idDeliveryDate"));
        cif.put("idExpiryDate", str(body, "idExpiryDate"));
        cif.put("maritalStatus", str(body, "maritalStatus"));
        cif.put("gender", str(body, "gender"));
        cif.put("motherFirstName", str(body, "motherFirstName"));
        cif.put("motherLastName", firstNonBlank(str(body, "motherLastName"), str(body, "motherName")));
        cif.put("mobile", firstNonBlank(normalizeMobile(str(body, "MobileNo")), normalizeMobile(str(body, "mobile"))));
        cif.put("email", str(body, "email"));
        cif.put("cifType", props.getImal().getCifType());
        cif.put("idType", props.getImal().getIdType());
        return cif;
    }

    private Map<String, Object> buildAccountDetails(Map<String, Object> body) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fullName", firstNonBlank(str(body, "fullName"), "CUSTOMER"));
        details.put("mailingAddress", str(body, "mailingAddress"));
        details.put("addressEn1", firstNonBlank(str(body, "addressEn1"), str(body, "mailingAddress")));
        details.put("addressEn2", str(body, "addressEn2"));
        details.put("cityEn", firstNonBlank(str(body, "cityEn"), str(body, "city")));
        details.put("homeTel", firstNonBlank(normalizeMobile(str(body, "MobileNo")), normalizeMobile(str(body, "mobile"))));
        details.put("email", str(body, "email"));
        details.put("postalZipCode", str(body, "postalZipCode"));
        details.put("economicSector", firstNonBlank(str(body, "economicSector"), props.getImal().getEconomicSector()));
        details.put("subEconomicSector", firstNonBlank(str(body, "subEconomicSector"), props.getImal().getSubEconomicSector()));
        return details;
    }

    private static boolean hasMatchingProduct(List<Map<String, Object>> accounts, String productCode, String accGl) {
        for (var a : accounts) {
            String p = a.get("productCode") != null ? String.valueOf(a.get("productCode")) : null;
            String g = a.get("accGl") != null ? String.valueOf(a.get("accGl")) : null;
            if (productCode.equals(p) || accGl.equals(g)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveCnic(Map<String, Object> body) {
        Object req = body.get("Request");
        if (req instanceof Map<?, ?> m && m.get("CNIC") != null) {
            return normalizeCnic(String.valueOf(m.get("CNIC")));
        }
        return normalizeCnic(str(body, "CNIC"));
    }

    private static SecurityConfig.BusinessException fail(HttpStatus status, String code, String desc, String step, String corr) {
        log.error("account FAIL step={} code={} desc={} corr={}", step, code, desc, corr);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Response_Code", code);
        body.put("Response_Desc", desc);
        body.put("failedStep", step);
        body.put("correlationId", corr);
        return new SecurityConfig.BusinessException(body, status);
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank() && !"null".equals(v)) return v;
        }
        return null;
    }

    private static String normalizeCnic(String cnic) {
        if (cnic == null || "null".equals(cnic)) return null;
        return cnic.replaceAll("[^0-9]", "");
    }

    private static String normalizeMobile(String mobile) {
        if (mobile == null || "null".equals(mobile)) return null;
        String digits = mobile.replaceAll("[^0-9]", "");
        if (digits.startsWith("92") && digits.length() == 12) {
            return "0" + digits.substring(2);
        }
        return digits.isBlank() ? null : digits;
    }
}
