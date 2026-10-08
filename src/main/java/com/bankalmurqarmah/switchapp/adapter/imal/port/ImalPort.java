package com.bankalmurqarmah.switchapp.adapter.imal.port;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Core-banking port. Callers: AccountService (open/list), InquiryService, PaymentService;
 * impl: MockImalAdapter. Rewritten for iMal CIF/account orchestration (plan).
 */
public interface ImalPort {
    Map<String, Object> balanceInquiry(String accountNumber);

    Map<String, Object> iftTitleFetch(String accountNumber);

    Map<String, Object> ibftTitleFetch(String toImd, String toIban, String toAccount);

    /** Accounts for CNIC including balance, productCode, accGl, cifNo. */
    Map<String, Object> listAccountsByCnic(String cnic);

    /** Accounts for CIF including balance, productCode, accGl, cifNo. */
    Map<String, Object> listAccountsByCif(String cifNo);

    Map<String, Object> customerDetail(String cnic);

    TransferResult iftPayment(String from, String to, BigDecimal amount, String stan, String purpose, String narration, String idempotencyKey);

    TransferResult ibftPayment(String from, String toIban, String toImd, String toAccount, BigDecimal amount, String stan, String purpose, String narration, String idempotencyKey);

    Map<String, Object> ibftStatus(String transactionId, String stan);

    /** Create or return existing CIF for CNIC. */
    CifResult createRetailCif(Map<String, Object> cifDetails);

    StatusResult validateRetailCif(String cifNo);

    AccountCreateResult createGeneralAccount(String cifNo, String accGl, String productCode, Map<String, Object> accountDetails);

    StatusResult authorizeGeneralAccount(String additionalRef);

    record TransferResult(String responseCode, String responseDesc, String stan, String transactionId, String status) {}

    record CifResult(String statusCode, String statusDesc, String cifNo, boolean created) {}

    record StatusResult(String statusCode, String statusDesc) {}

    record AccountCreateResult(String statusCode, String statusDesc, String additionalRef, String iban,
                               String cifNo, String accGl) {}
}
