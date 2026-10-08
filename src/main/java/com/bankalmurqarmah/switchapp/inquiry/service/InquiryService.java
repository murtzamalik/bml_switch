package com.bankalmurqarmah.switchapp.inquiry.service;

import com.bankalmurqarmah.switchapp.adapter.imal.port.ImalPort;
import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class InquiryService {
    private final ImalPort imalPort;
    private final JdbcTemplate jdbc;
    private final SwitchProperties props;

    public InquiryService(ImalPort imalPort, JdbcTemplate jdbc, SwitchProperties props) {
        this.imalPort = imalPort;
        this.jdbc = jdbc;
        this.props = props;
    }

    public Map<String, Object> balance(Map<String, Object> body, HttpServletRequest request, String corr) {
        Map<String, Object> bal = imalPort.balanceInquiry(str(body, "fromAccount"));
        Map<String, Object> resp = new LinkedHashMap<>();
        if (!"00".equals(bal.get("code"))) {
            resp.put("Response_Code", bal.get("code"));
            resp.put("Response_Desc", bal.get("desc"));
            resp.put("correlationId", corr);
            return resp;
        }
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("Amount", bal.get("amount"));
        ret.put("BranchCode", bal.get("branchCode"));
        ret.put("FromAccount", bal.get("fromAccount"));
        ret.put("CURRENCY_CODE", bal.get("currency"));
        resp.put("Return", ret);
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("correlationId", corr);
        return resp;
    }

    public Map<String, Object> iftTitle(Map<String, Object> body, HttpServletRequest request, String corr) {
        String account = str(body, "Account");
        if (account == null) account = str(body, "fromAccount");
        Map<String, Object> title = imalPort.iftTitleFetch(account);
        Map<String, Object> resp = new LinkedHashMap<>();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("accountTitle", title.getOrDefault("accountTitle", ""));
        nested.put("account", title.getOrDefault("account", account));
        nested.put("agentAccount", "");
        nested.put("channelId", props.getChannelId());
        nested.put("fromAccount", "");
        nested.put("password", "");
        nested.put("processingCode", "");
        nested.put("stan", "");
        nested.put("status", title.getOrDefault("code", "14"));
        nested.put("statusDescription", "00".equals(title.get("code")) ? "Success" : title.getOrDefault("desc", "Failed"));
        nested.put("userId", "");
        nested.put("TransmissionDateTime", "");
        nested.put("idType", "");
        nested.put("idValue", "");
        resp.put("return", nested);
        resp.put("Response_Code", title.getOrDefault("code", "14"));
        resp.put("Response_Desc", "00".equals(title.get("code")) ? "Success" : String.valueOf(title.getOrDefault("desc", "Failed")));
        resp.put("correlationId", corr);
        return resp;
    }

    public Map<String, Object> ibftTitle(Map<String, Object> body, HttpServletRequest request, String corr) {
        Map<String, Object> title = imalPort.ibftTitleFetch(str(body, "toIMD"), str(body, "toIBAN"), str(body, "toAccount"));
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("accountTitle", title.getOrDefault("accountTitle", ""));
        inner.put("agentAccount", "");
        inner.put("amount", str(body, "amount") != null ? str(body, "amount") : "");
        inner.put("dateAndTime", "");
        inner.put("fromAccount", str(body, "fromAccount") != null ? str(body, "fromAccount") : "");
        inner.put("password", "");
        inner.put("processingCode", "");
        inner.put("recordData", "");
        inner.put("stan", "");
        inner.put("toAccount", title.getOrDefault("toAccount", ""));
        inner.put("toIBAN", title.getOrDefault("toIBAN", ""));
        inner.put("toIMD", title.getOrDefault("toIMD", ""));
        inner.put("transactionID", "");
        inner.put("userId", "");
        inner.put("webService", "");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("IBFTTitleFetchResponse", Map.of("return", inner));
        resp.put("Response_Code", title.getOrDefault("code", "14"));
        resp.put("Response_Desc", "00".equals(title.get("code")) ? "Success" : String.valueOf(title.getOrDefault("desc", "Failed")));
        resp.put("correlationId", corr);
        return resp;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> cnic(Map<String, Object> body, HttpServletRequest request, String corr) {
        String cnic = str(body, "CNIC");
        Map<String, Object> list = imalPort.listAccountsByCnic(cnic);
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) list.get("accounts");
        Object item;
        if (accounts == null || accounts.isEmpty()) {
            item = Map.of("Title", "", "AccountNumber", "");
        } else if (accounts.size() == 1) {
            // Keys from ImalPort.listAccountsByCnic (accountTitle/accountNumber) after onboarding rewrite
            item = Map.of("Title", accounts.get(0).get("accountTitle"), "AccountNumber", accounts.get(0).get("accountNumber"));
        } else {
            List<Map<String, Object>> items = new ArrayList<>();
            for (var a : accounts) {
                items.add(Map.of("Title", a.get("accountTitle"), "AccountNumber", a.get("accountNumber")));
            }
            item = items;
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Res", Map.of(
                "NS1", "",
                "GetAccountFromCNICItems", Map.of("Item", item),
                "Result", "Success"));
        resp.put("correlationId", corr);
        return resp;
    }

    public Map<String, Object> ibftStatus(Map<String, Object> body, HttpServletRequest request, String corr) {
        Map<String, Object> st = imalPort.ibftStatus(str(body, "transactionID"), str(body, "stan") != null ? str(body, "stan") : str(body, "intRefNum"));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", st.getOrDefault("code", "14"));
        resp.put("Response_Desc", "00".equals(st.get("code")) ? "Success" : "Not found");
        resp.put("status", st.getOrDefault("status", "FAILED"));
        resp.put("stan", st.getOrDefault("stan", ""));
        resp.put("transactionID", st.getOrDefault("transactionID", ""));
        resp.put("amount", st.getOrDefault("amount", ""));
        resp.put("fee", st.getOrDefault("fee", "0.00"));
        resp.put("correlationId", corr);
        return resp;
    }

    public Map<String, Object> miniStatement(Map<String, Object> body, HttpServletRequest request, String corr) {
        String acct = str(body, "fromAccount");
        int limit = 10;
        if (str(body, "limit") != null) {
            try { limit = Integer.parseInt(str(body, "limit")); } catch (Exception ignored) {}
        }
        var acctRows = jdbc.queryForList("SELECT id FROM accounts WHERE account_number=?", acct);
        List<Map<String, Object>> txns = List.of();
        if (!acctRows.isEmpty()) {
            txns = jdbc.queryForList("""
                    SELECT booking_date as bookingDate, narrative as narration, direction, amount, currency, running_balance as balanceAfter, stan, id as ref
                    FROM statement_entries WHERE account_id=? ORDER BY created_at DESC LIMIT ?
                    """, acctRows.get(0).get("id"), limit);
            for (var t : txns) {
                if (t.get("amount") instanceof BigDecimal bd) {
                    t.put("amount", bd.setScale(2, RoundingMode.HALF_UP).toPlainString());
                }
                if (t.get("balanceAfter") instanceof BigDecimal bd) {
                    t.put("balanceAfter", bd.setScale(2, RoundingMode.HALF_UP).toPlainString());
                }
                if (t.get("bookingDate") != null) {
                    t.put("bookingDate", t.get("bookingDate").toString());
                }
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("correlationId", corr);
        resp.put("transactions", txns);
        return resp;
    }

    public Map<String, Object> statement(Map<String, Object> body, HttpServletRequest request, String corr) {
        String acct = str(body, "fromAccount");
        LocalDate from = LocalDate.parse(str(body, "fromDate"));
        LocalDate to = LocalDate.parse(str(body, "toDate"));
        if (from.isAfter(to) || from.plusDays(90).isBefore(to)) {
            return Map.of("Response_Code", "61", "Response_Desc", "Limit exceeded", "correlationId", corr, "transactions", List.of());
        }
        var acctRows = jdbc.queryForList("SELECT id FROM accounts WHERE account_number=?", acct);
        List<Map<String, Object>> txns = List.of();
        if (!acctRows.isEmpty()) {
            txns = jdbc.queryForList("""
                    SELECT booking_date as bookingDate, narrative as narration, direction, amount, currency, running_balance as balanceAfter, stan, id as ref
                    FROM statement_entries WHERE account_id=? AND booking_date BETWEEN ? AND ? ORDER BY booking_date DESC, created_at DESC
                    """, acctRows.get(0).get("id"), java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
            for (var t : txns) {
                if (t.get("amount") instanceof BigDecimal bd) t.put("amount", bd.setScale(2, RoundingMode.HALF_UP).toPlainString());
                if (t.get("balanceAfter") instanceof BigDecimal bd) t.put("balanceAfter", bd.setScale(2, RoundingMode.HALF_UP).toPlainString());
                if (t.get("bookingDate") != null) t.put("bookingDate", t.get("bookingDate").toString());
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("correlationId", corr);
        resp.put("transactions", txns);
        return resp;
    }

    public Map<String, Object> limits(Map<String, Object> body, HttpServletRequest request, String corr) {
        String acct = str(body, "fromAccount");
        BigDecimal used = BigDecimal.ZERO;
        try {
            used = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(t.amount),0) FROM transactions t
                    JOIN accounts a ON a.id=t.account_id
                    WHERE a.account_number=? AND t.direction='DEBIT' AND t.status='POSTED'
                      AND DATE(t.created_at)=CURRENT_DATE
                    """, BigDecimal.class, acct);
        } catch (Exception ignored) {
        }
        if (used == null) used = BigDecimal.ZERO;
        BigDecimal limit = props.getAsaan().getDailyDebitLimit();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("dailyDebitLimit", limit.setScale(2, RoundingMode.HALF_UP).toPlainString());
        resp.put("dailyDebitUsed", used.setScale(2, RoundingMode.HALF_UP).toPlainString());
        resp.put("dailyDebitRemaining", limit.subtract(used).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString());
        resp.put("maxBalance", props.getAsaan().getMaxBalance().setScale(2, RoundingMode.HALF_UP).toPlainString());
        resp.put("currency", "PKR");
        resp.put("correlationId", corr);
        return resp;
    }

    public Map<String, Object> receipt(Map<String, Object> body, HttpServletRequest request, String corr) {
        String stan = str(body, "stan");
        String txId = str(body, "transactionID");
        List<Map<String, Object>> rows;
        if (txId != null && !txId.isBlank()) {
            rows = jdbc.queryForList("SELECT * FROM transactions WHERE transaction_id=? AND direction='DEBIT' LIMIT 1", txId);
        } else {
            rows = jdbc.queryForList("SELECT * FROM transactions WHERE stan=? AND direction='DEBIT' ORDER BY created_at DESC LIMIT 1", stan);
        }
        if (rows.isEmpty()) {
            return Map.of("Response_Code", "14", "Response_Desc", "Not found", "correlationId", corr);
        }
        var t = rows.get(0);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("Response_Code", "00");
        resp.put("Response_Desc", "Success");
        resp.put("stan", t.get("stan"));
        resp.put("transactionID", t.get("transaction_id"));
        resp.put("amount", ((BigDecimal) t.get("amount")).setScale(2, RoundingMode.HALF_UP).toPlainString());
        resp.put("fee", "0.00");
        resp.put("status", t.get("status"));
        resp.put("fromAccount", null);
        resp.put("toAccount", t.get("counterparty_account"));
        resp.put("toIBAN", t.get("counterparty_iban"));
        resp.put("correlationId", corr);
        return resp;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
