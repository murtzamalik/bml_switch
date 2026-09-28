package com.bankalmurqarmah.switchapp.adapter.imal.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ImalPort {
    Map<String, Object> balanceInquiry(String accountNumber);
    Map<String, Object> iftTitleFetch(String accountNumber);
    Map<String, Object> ibftTitleFetch(String toImd, String toIban, String toAccount);
    Map<String, Object> listAccountsByCnic(String cnic);
    Map<String, Object> customerDetail(String cnic);
    TransferResult iftPayment(String from, String to, BigDecimal amount, String stan, String purpose, String narration, String idempotencyKey);
    TransferResult ibftPayment(String from, String toIban, String toImd, String toAccount, BigDecimal amount, String stan, String purpose, String narration, String idempotencyKey);
    Map<String, Object> ibftStatus(String transactionId, String stan);
    String openAccount(String cnic, String fullName, String mobile, String productCode);

    record TransferResult(String responseCode, String responseDesc, String stan, String transactionId, String status) {}
}
