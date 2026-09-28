package com.bankalmurqarmah.switchapp.adapter.imal.mock;

import com.bankalmurqarmah.switchapp.adapter.imal.port.ImalPort;
import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Component
@ConditionalOnProperty(name = "switch.imal.mock", havingValue = "true", matchIfMissing = true)
public class MockImalAdapter implements ImalPort {
    private final JdbcTemplate jdbc;
    private final SwitchProperties props;

    public MockImalAdapter(JdbcTemplate jdbc, SwitchProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Override
    public Map<String, Object> balanceInquiry(String accountNumber) {
        if ("0345000000000".equals(accountNumber)) {
            return Map.of("code", "14", "desc", "Account not found");
        }
        var rows = jdbc.queryForList("""
                SELECT a.account_number, a.branch_code, a.currency, a.status, lb.available_balance
                FROM accounts a JOIN ledger_balances lb ON lb.account_id=a.id
                WHERE a.account_number=? AND a.deleted_at IS NULL
                """, accountNumber);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.get(0).get("status"))) {
            return Map.of("code", "12", "desc", "Invalid or closed account");
        }
        var r = rows.get(0);
        return Map.of(
                "code", "00",
                "amount", ((BigDecimal) r.get("available_balance")).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "branchCode", r.get("branch_code"),
                "fromAccount", r.get("account_number"),
                "currency", r.get("currency"));
    }

    @Override
    public Map<String, Object> iftTitleFetch(String accountNumber) {
        var rows = jdbc.queryForList(
                "SELECT account_number, account_title, status FROM accounts WHERE account_number=? AND deleted_at IS NULL",
                accountNumber);
        if (rows.isEmpty()) {
            return Map.of("code", "14", "desc", "Account not found");
        }
        return Map.of(
                "code", "00",
                "accountTitle", rows.get(0).get("account_title"),
                "account", rows.get(0).get("account_number"),
                "status", "00",
                "statusDescription", "Success");
    }

    @Override
    public Map<String, Object> ibftTitleFetch(String toImd, String toIban, String toAccount) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ref_banks WHERE imd=? AND is_active=1 AND supports_ibft=1 AND deleted_at IS NULL",
                Integer.class, toImd);
        if (cnt == null || cnt == 0) {
            return Map.of("code", "76", "desc", "Invalid or inactive IMD");
        }
        String title = "EXTERNAL BENEFICIARY";
        if (toIban != null && toIban.contains("MEZN")) {
            title = "ALI KHAN";
        }
        return Map.of(
                "code", "00",
                "accountTitle", title,
                "toAccount", toAccount != null ? toAccount : "",
                "toIBAN", toIban != null ? toIban : "",
                "toIMD", toImd);
    }

    @Override
    public Map<String, Object> listAccountsByCnic(String cnic) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.account_number, a.iban, a.account_title, a.account_type, a.currency, a.status
                FROM accounts a JOIN customers c ON c.id=a.customer_id
                WHERE c.cnic=? AND a.deleted_at IS NULL
                """, cnic);
        return Map.of("code", "00", "accounts", rows);
    }

    @Override
    public Map<String, Object> customerDetail(String cnic) {
        var rows = jdbc.queryForList(
                "SELECT cnic, full_name, email, mobile FROM customers WHERE cnic=? AND deleted_at IS NULL", cnic);
        if (rows.isEmpty()) {
            return Map.of("code", "14", "desc", "Customer not found",
                    "CNIC", cnic, "FULL_NAME", "", "Email", "", "ADDRESS", "", "MOBILE_NUM", "", "D_Birth", "");
        }
        var r = rows.get(0);
        return Map.of(
                "code", "00",
                "CNIC", r.get("cnic"),
                "FULL_NAME", r.get("full_name"),
                "Email", r.get("email") != null ? r.get("email") : "",
                "ADDRESS", "House 123, Street 4, Karachi",
                "MOBILE_NUM", r.get("mobile") != null ? r.get("mobile") : "",
                "D_Birth", "1990-01-01");
    }

    @Override
    @Transactional
    public TransferResult iftPayment(String from, String to, BigDecimal amount, String stan, String purpose, String narration, String idempotencyKey) {
        return transferInternal(from, to, null, null, amount, stan, purpose, narration, idempotencyKey, "IFT");
    }

    @Override
    @Transactional
    public TransferResult ibftPayment(String from, String toIban, String toImd, String toAccount, BigDecimal amount, String stan, String purpose, String narration, String idempotencyKey) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ref_banks WHERE imd=? AND is_active=1 AND supports_ibft=1", Integer.class, toImd);
        if (cnt == null || cnt == 0) {
            return new TransferResult("76", "Invalid or inactive IMD", stan, null, "FAILED");
        }
        return transferInternal(from, toAccount, toIban, toImd, amount, stan, purpose, narration, idempotencyKey, "IBFT");
    }

    private TransferResult transferInternal(String from, String toAccount, String toIban, String toImd,
                                            BigDecimal amount, String stan, String purpose, String narration,
                                            String idempotencyKey, String type) {
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        var fromRows = jdbc.queryForList("""
                SELECT a.id, a.status, lb.available_balance, lb.id as bal_id
                FROM accounts a JOIN ledger_balances lb ON lb.account_id=a.id
                WHERE a.account_number=? AND a.deleted_at IS NULL
                """, from);
        if (fromRows.isEmpty() || !"ACTIVE".equals(fromRows.get(0).get("status"))) {
            return new TransferResult("12", "Invalid or closed account", stan, null, "FAILED");
        }
        BigDecimal bal = (BigDecimal) fromRows.get(0).get("available_balance");
        if (bal.compareTo(amount) < 0) {
            return new TransferResult("51", "Insufficient funds", stan, null, "FAILED");
        }
        BigDecimal usedToday = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM transactions
                WHERE account_id=? AND direction='DEBIT' AND status='POSTED' AND DATE(created_at)=CURRENT_DATE
                """, BigDecimal.class, fromRows.get(0).get("id"));
        if (usedToday == null) usedToday = BigDecimal.ZERO;
        if (usedToday.add(amount).compareTo(props.getAsaan().getDailyDebitLimit()) > 0) {
            return new TransferResult("61", "Limit exceeded", stan, null, "FAILED");
        }
        if ("IFT".equals(type)) {
            var toRows = jdbc.queryForList(
                    "SELECT id, status FROM accounts WHERE account_number=? AND deleted_at IS NULL", toAccount);
            if (toRows.isEmpty() || !"ACTIVE".equals(toRows.get(0).get("status"))) {
                return new TransferResult("14", "Account not found", stan, null, "FAILED");
            }
        }
        String fromId = String.valueOf(fromRows.get(0).get("id"));
        String txId = "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String debitId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO transactions(id, account_id, counterparty_account, counterparty_iban, to_imd, direction, txn_type,
                  amount, currency, fee, status, stan, transaction_id, purpose_of_payment, narration, idempotency_key)
                VALUES (?,?,?,?,?,'DEBIT',?,?, 'PKR', 0, 'POSTED',?,?,?,?,?)
                """, debitId, fromId, toAccount, toIban, toImd, type, amount, stan, txId, purpose, narration, idempotencyKey);
        BigDecimal newBal = bal.subtract(amount);
        jdbc.update("UPDATE ledger_balances SET available_balance=?, ledger_balance=?, as_of=CURRENT_TIMESTAMP(3) WHERE account_id=?",
                newBal, newBal, fromId);
        jdbc.update("""
                INSERT INTO statement_entries(id, account_id, transaction_id, booking_date, amount, currency, direction, running_balance, narrative, stan)
                VALUES (?,?,?,?,?,'PKR','DEBIT',?,?,?)
                """, UUID.randomUUID().toString(), fromId, debitId, Date.valueOf(LocalDate.now()), amount, newBal,
                narration != null ? narration : type + " debit", stan);

        if ("IFT".equals(type) && toAccount != null) {
            var toRows = jdbc.queryForList("""
                    SELECT a.id, lb.available_balance FROM accounts a JOIN ledger_balances lb ON lb.account_id=a.id
                    WHERE a.account_number=?
                    """, toAccount);
            String toId = String.valueOf(toRows.get(0).get("id"));
            BigDecimal toBal = ((BigDecimal) toRows.get(0).get("available_balance")).add(amount);
            jdbc.update("UPDATE ledger_balances SET available_balance=?, ledger_balance=?, as_of=CURRENT_TIMESTAMP(3) WHERE account_id=?",
                    toBal, toBal, toId);
            String creditId = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO transactions(id, account_id, counterparty_account, direction, txn_type, amount, currency, fee, status, stan, transaction_id, purpose_of_payment, narration, idempotency_key)
                    VALUES (?,?,?,'CREDIT',?,?,'PKR',0,'POSTED',?,?,?,?,?)
                    """, creditId, toId, from, type, amount, stan, txId, purpose, narration, idempotencyKey);
            jdbc.update("""
                    INSERT INTO statement_entries(id, account_id, transaction_id, booking_date, amount, currency, direction, running_balance, narrative, stan)
                    VALUES (?,?,?,?,?,'PKR','CREDIT',?,?,?)
                    """, UUID.randomUUID().toString(), toId, creditId, Date.valueOf(LocalDate.now()), amount, toBal,
                    narration != null ? narration : type + " credit", stan);
        }
        return new TransferResult("00", "Success", stan, txId, "POSTED");
    }

    @Override
    public Map<String, Object> ibftStatus(String transactionId, String stan) {
        List<Map<String, Object>> rows;
        if (transactionId != null && !transactionId.isBlank()) {
            rows = jdbc.queryForList("SELECT status, stan, transaction_id, amount FROM transactions WHERE transaction_id=? AND direction='DEBIT' LIMIT 1", transactionId);
        } else {
            rows = jdbc.queryForList("SELECT status, stan, transaction_id, amount FROM transactions WHERE stan=? AND direction='DEBIT' ORDER BY created_at DESC LIMIT 1", stan);
        }
        if (rows.isEmpty()) {
            return Map.of("code", "14", "status", "FAILED", "desc", "Not found");
        }
        return Map.of(
                "code", "00",
                "status", rows.get(0).get("status"),
                "stan", rows.get(0).get("stan"),
                "transactionID", rows.get(0).get("transaction_id"),
                "amount", ((BigDecimal) rows.get(0).get("amount")).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "fee", "0.00");
    }

    @Override
    @Transactional
    public String openAccount(String cnic, String fullName, String mobile, String productCode) {
        var existing = jdbc.queryForList("""
                SELECT a.account_number FROM accounts a JOIN customers c ON c.id=a.customer_id
                WHERE c.cnic=? AND a.product_code=? AND a.status='ACTIVE' LIMIT 1
                """, cnic, productCode != null ? productCode : "ASAAN_DIGITAL");
        if (!existing.isEmpty()) {
            return String.valueOf(existing.get(0).get("account_number"));
        }
        var cust = jdbc.queryForList("SELECT id FROM customers WHERE cnic=?", cnic);
        String customerId;
        if (cust.isEmpty()) {
            customerId = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO customers(id, cnic, full_name, mobile, status) VALUES (?,?,?,?, 'ACTIVE')
                    """, customerId, cnic, fullName, mobile);
        } else {
            customerId = String.valueOf(cust.get(0).get("id"));
        }
        String acct = "0345" + String.format("%09d", new Random().nextInt(1_000_000_000));
        String iban = "PK00" + props.getBank().getIbanCode() + "000000" + acct;
        String acctId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO accounts(id, customer_id, account_number, iban, account_title, account_type, product_code, branch_code, currency, status)
                VALUES (?,?,?,?,?,'CURRENT',?,?, 'PKR','ACTIVE')
                """, acctId, customerId, acct, iban, fullName,
                productCode != null ? productCode : "ASAAN_DIGITAL", props.getBank().getBranchDefault());
        jdbc.update("""
                INSERT INTO ledger_balances(id, account_id, available_balance, ledger_balance, currency)
                VALUES (?,?,0,0,'PKR')
                """, UUID.randomUUID().toString(), acctId);
        return acct;
    }
}
