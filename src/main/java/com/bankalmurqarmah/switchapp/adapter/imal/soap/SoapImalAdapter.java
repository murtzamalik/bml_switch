package com.bankalmurqarmah.switchapp.adapter.imal.soap;

import com.bankalmurqarmah.switchapp.adapter.imal.port.ImalPort;
import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Live iMal SOAP for open + IFT; list/balance stay on local DB (mock reads).
 * Callers: AccountService (open), PaymentService (ift), InquiryService (list/balance).
 * Activated when switch.imal.mock=false. No prior SoapImalAdapter existed (only MockImalAdapter).
 * Syncs CIF/account rows to MySQL after SOAP success so Option P / account-list work.
 * User: continue live iMal for open+IFT; account-list and balance remain mock.
 */
@Component
@ConditionalOnProperty(name = "switch.imal.mock", havingValue = "false")
public class SoapImalAdapter implements ImalPort {
    private static final Logger log = LoggerFactory.getLogger(SoapImalAdapter.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final ImalSoapClient soap;
    private final SwitchProperties props;
    private final JdbcTemplate jdbc;

    public SoapImalAdapter(ImalSoapClient soap, SwitchProperties props, JdbcTemplate jdbc) {
        this.soap = soap;
        this.props = props;
        this.jdbc = jdbc;
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
        String title = toIban != null && toIban.contains("MEZN") ? "ALI KHAN" : "EXTERNAL BENEFICIARY";
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
                SELECT a.account_number, a.iban, a.account_title, a.account_type, a.product_code, a.acc_gl,
                       a.cif_no, a.currency, a.status, lb.available_balance
                FROM accounts a
                JOIN customers c ON c.id=a.customer_id
                JOIN ledger_balances lb ON lb.account_id=a.id
                WHERE c.cnic=? AND a.deleted_at IS NULL AND a.status='ACTIVE'
                """, cnic);
        return Map.of("code", "00", "accounts", mapAccountRows(rows));
    }

    @Override
    public Map<String, Object> listAccountsByCif(String cifNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.account_number, a.iban, a.account_title, a.account_type, a.product_code, a.acc_gl,
                       a.cif_no, a.currency, a.status, lb.available_balance
                FROM accounts a
                JOIN ledger_balances lb ON lb.account_id=a.id
                WHERE a.cif_no=? AND a.deleted_at IS NULL AND a.status='ACTIVE'
                """, cifNo);
        return Map.of("code", "00", "accounts", mapAccountRows(rows));
    }

    private List<Map<String, Object>> mapAccountRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("accountNumber", r.get("account_number"));
            item.put("IBAN", r.get("iban"));
            item.put("accountTitle", r.get("account_title"));
            item.put("accountType", r.get("account_type"));
            item.put("productCode", r.get("product_code"));
            item.put("accGl", r.get("acc_gl"));
            item.put("cifNo", r.get("cif_no"));
            item.put("currency", r.get("currency"));
            item.put("status", r.get("status"));
            BigDecimal bal = (BigDecimal) r.get("available_balance");
            item.put("balance", bal != null ? bal.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00");
            out.add(item);
        }
        return out;
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
                "ADDRESS", "",
                "MOBILE_NUM", r.get("mobile") != null ? r.get("mobile") : "",
                "D_Birth", "");
    }

    @Override
    public Map<String, Object> ibftStatus(String transactionId, String stan) {
        List<Map<String, Object>> rows;
        if (transactionId != null && !transactionId.isBlank()) {
            rows = jdbc.queryForList(
                    "SELECT status, stan, transaction_id, amount FROM transactions WHERE transaction_id=? AND direction='DEBIT' LIMIT 1",
                    transactionId);
        } else {
            rows = jdbc.queryForList(
                    "SELECT status, stan, transaction_id, amount FROM transactions WHERE stan=? AND direction='DEBIT' ORDER BY created_at DESC LIMIT 1",
                    stan);
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
    public TransferResult ibftPayment(String from, String toIban, String toImd, String toAccount,
                                      BigDecimal amount, String stan, String purpose, String narration, String idempotencyKey) {
        return new TransferResult("96", "IBFT live SOAP not configured", stan, null, "FAILED");
    }

    @Override
    public CifResult createRetailCif(Map<String, Object> cifDetails) {
        String cnic = String.valueOf(cifDetails.get("idNumber"));
        var local = jdbc.queryForList(
                "SELECT cif_no FROM customers WHERE cnic=? AND cif_no IS NOT NULL AND deleted_at IS NULL", cnic);
        if (!local.isEmpty()) {
            return new CifResult("0", "Success", String.valueOf(local.get(0).get("cif_no")), false);
        }

        String response = soap.post(props.getImal().getSoap().getCifUrl(),
                buildCreateRetailCif(cifDetails), "createRetailCif");
        String cifNo = ImalSoapClient.firstTag(response, "cifNo");
        boolean ok = ImalSoapClient.success(response);
        if (!ok && (cifNo == null || cifNo.isBlank())) {
            String desc = ImalSoapClient.firstTag(response, "statusDesc");
            String code = ImalSoapClient.firstTag(response, "statusCode");
            return new CifResult(code != null ? code : "1", desc != null ? desc : "CIF create failed", null, false);
        }
        if (cifNo == null || cifNo.isBlank()) {
            return new CifResult("1", "CIF create succeeded but cifNo missing", null, false);
        }
        syncCustomer(cnic, cifNo, cifDetails);
        return new CifResult("0", "Success", cifNo, ok);
    }

    @Override
    public StatusResult validateRetailCif(String cifNo) {
        String response = soap.post(props.getImal().getSoap().getCifUrl(),
                buildValidateRetailCif(cifNo), "validateRetailCif");
        if (!ImalSoapClient.success(response)) {
            String desc = ImalSoapClient.firstTag(response, "statusDesc");
            String code = ImalSoapClient.firstTag(response, "statusCode");
            return new StatusResult(code != null ? code : "1", desc != null ? desc : "CIF validation failed");
        }
        return new StatusResult("0", "Success");
    }

    @Override
    public AccountCreateResult createGeneralAccount(String cifNo, String accGl, String productCode,
                                                    Map<String, Object> accountDetails) {
        String response = soap.post(props.getImal().getSoap().getAccountUrl(),
                buildCreateGeneralAccount(cifNo, accGl, accountDetails), "createGeneralAccount");
        if (!ImalSoapClient.success(response)) {
            String desc = ImalSoapClient.firstTag(response, "statusDesc");
            String code = ImalSoapClient.firstTag(response, "statusCode");
            return new AccountCreateResult(code != null ? code : "1",
                    desc != null ? desc : "Account create failed", null, null, cifNo, accGl);
        }
        String additionalRef = ImalSoapClient.firstTag(response, "additionalRef");
        String iban = ImalSoapClient.firstTag(response, "ibanAccNo");
        if (additionalRef == null || additionalRef.isBlank()) {
            return new AccountCreateResult("1", "Account create missing additionalRef", null, null, cifNo, accGl);
        }
        String fullName = accountDetails != null && accountDetails.get("fullName") != null
                ? String.valueOf(accountDetails.get("fullName")) : "CUSTOMER";
        syncAccount(cifNo, additionalRef, iban, fullName, productCode, accGl);
        return new AccountCreateResult("0", "Success", additionalRef, iban, cifNo, accGl);
    }

    @Override
    public StatusResult authorizeGeneralAccount(String additionalRef) {
        String response = soap.post(props.getImal().getSoap().getAccountUrl(),
                buildAuthorizeGeneralAccount(additionalRef), "authorizeGeneralAccount");
        if (!ImalSoapClient.success(response)) {
            String desc = ImalSoapClient.firstTag(response, "statusDesc");
            String code = ImalSoapClient.firstTag(response, "statusCode");
            return new StatusResult(code != null ? code : "1", desc != null ? desc : "Authorization failed");
        }
        jdbc.update("UPDATE accounts SET status='ACTIVE' WHERE account_number=?", additionalRef);
        return new StatusResult("0", "Success");
    }

    @Override
    public TransferResult iftPayment(String from, String to, BigDecimal amount, String stan,
                                     String purpose, String narration, String idempotencyKey) {
        if (from == null || to == null || amount == null) {
            return new TransferResult("30", "VALIDATION_ERROR", stan, null, "FAILED");
        }
        try {
            String response = soap.post(props.getImal().getSoap().getTransferUrl(),
                    buildCreateTransfer(from, to, amount, narration != null ? narration : purpose),
                    "createTransfer");
            if (!ImalSoapClient.success(response)) {
                String desc = ImalSoapClient.firstTag(response, "statusDesc");
                return new TransferResult("96", desc != null ? desc : "Transfer failed", stan, null, "FAILED");
            }
            String txnNo = ImalSoapClient.firstTag(response, "transactionNumber");
            String txId = txnNo != null ? "IMAL-" + txnNo : "IMAL-" + UUID.randomUUID().toString().substring(0, 8);
            return new TransferResult("00", "Success", stan, txId, "POSTED");
        } catch (ImalSoapClient.ImalSoapException e) {
            log.error("IFT SOAP failed op={} msg={} response={}",
                    e.getOperation(), e.getMessage(), e.getResponseXml());
            return new TransferResult("96", e.getMessage(), stan, null, "FAILED");
        }
    }

    private String buildCreateRetailCif(Map<String, Object> d) {
        var imal = props.getImal();
        String fullName = str(d, "fullName", "CUSTOMER");
        String first = str(d, "firstName", fullName.contains(" ") ? fullName.substring(0, fullName.indexOf(' ')) : fullName);
        String last = str(d, "lastName", fullName.contains(" ") ? fullName.substring(fullName.lastIndexOf(' ') + 1) : fullName);
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cif="cifManagementWs">
                   <soapenv:Header/>
                   <soapenv:Body>
                      <cif:createRetailCif>
                         <serviceContext>
                            <businessArea>Retail</businessArea>
                            <businessDomain>Products</businessDomain>
                            <operationName>createRetailCif</operationName>
                            <serviceDomain>CifManagement</serviceDomain>
                            <serviceID>1901</serviceID>
                            <version>1.0</version>
                         </serviceContext>
                         <companyCode>%s</companyCode>
                         <branchCode>%s</branchCode>
                         <cifDetails>
                            <cifType>%s</cifType>
                            <idType>%s</idType>
                            <idNumber>%s</idNumber>
                            <idDeliveryDate>%s</idDeliveryDate>
                            <dateOfBirth>%s</dateOfBirth>
                            <idExpiryDate>%s</idExpiryDate>
                            <maritalStatus>%s</maritalStatus>
                            <gender>%s</gender>
                            <language>L</language>
                            <fullName>%s</fullName>
                            <nationality>586</nationality>
                            <country>586</country>
                            <firstName>%s</firstName>
                            <lastName>%s</lastName>
                            <motherFirstName>%s</motherFirstName>
                            <motherLastName>%s</motherLastName>
                            <addressList>
                               <addressDetailsCreateDC>
                                  <mobile>%s</mobile>
                                  <email>%s</email>
                                  <country>586</country>
                               </addressDetailsCreateDC>
                            </addressList>
                            <modeOfStatementDelivery>N</modeOfStatementDelivery>
                            <statement>N</statement>
                         </cifDetails>
                         <additionalDetails>
                            <ranking>1</ranking>
                            <monthlyIncome>50000</monthlyIncome>
                            <kyc>Y</kyc>
                         </additionalDetails>
                         %s
                         %s
                      </cif:createRetailCif>
                   </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(
                e(imal.getCompanyCode()), e(imal.getBranchCode()), e(imal.getCifType()), e(imal.getIdType()),
                e(str(d, "idNumber", "")), e(str(d, "idDeliveryDate", "2018-01-01")),
                e(str(d, "dateOfBirth", "1990-01-01")), e(str(d, "idExpiryDate", "2030-01-01")),
                e(str(d, "maritalStatus", "M")), e(str(d, "gender", "M")), e(fullName), e(first), e(last),
                e(str(d, "motherFirstName", "TEST")), e(str(d, "motherLastName", "TEST")),
                e(str(d, "mobile", "")), e(str(d, "email", "")),
                requesterContext(false), vendorContext());
    }

    private String buildValidateRetailCif(String cifNo) {
        var imal = props.getImal();
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cif="cifManagementWs">
                   <soapenv:Header/>
                   <soapenv:Body>
                      <cif:validateRetailCif>
                         <serviceContext>
                            <businessArea>Retail</businessArea>
                            <businessDomain>Products</businessDomain>
                            <operationName>validateRetailCif</operationName>
                            <serviceDomain>CifManagement</serviceDomain>
                            <serviceID>1903</serviceID>
                            <version>1.0</version>
                         </serviceContext>
                         <companyCode>%s</companyCode>
                         <branchCode>%s</branchCode>
                         <cifNo>%s</cifNo>
                         %s
                         %s
                      </cif:validateRetailCif>
                   </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(e(imal.getCompanyCode()), e(imal.getBranchCode()), e(cifNo),
                requesterContext(true), vendorContext());
    }

    private String buildCreateGeneralAccount(String cifNo, String accGl, Map<String, Object> d) {
        var imal = props.getImal();
        String fullName = d != null ? str(d, "fullName", "CUSTOMER") : "CUSTOMER";
        String addr1 = d != null ? str(d, "addressEn1", str(d, "mailingAddress", "N/A")) : "N/A";
        String addr2 = d != null ? str(d, "addressEn2", "") : "";
        String city = d != null ? str(d, "cityEn", "KARACHI") : "KARACHI";
        String tel = d != null ? str(d, "homeTel", "") : "";
        String email = d != null ? str(d, "email", "") : "";
        String zip = d != null ? str(d, "postalZipCode", "00000") : "00000";
        String econ = d != null ? str(d, "economicSector", imal.getEconomicSector()) : imal.getEconomicSector();
        String sub = d != null ? str(d, "subEconomicSector", imal.getSubEconomicSector()) : imal.getSubEconomicSector();
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:gen="generalAccountsWs">
                   <soapenv:Header/>
                   <soapenv:Body>
                      <gen:createGeneralAccount>
                         <serviceContext>
                            <businessArea>Retail</businessArea>
                            <businessDomain>Products</businessDomain>
                            <operationName>createGeneralAccount</operationName>
                            <serviceDomain>GeneralAccounts</serviceDomain>
                            <serviceID>806</serviceID>
                            <version>1.0</version>
                         </serviceContext>
                         <companyCode>%s</companyCode>
                         <branchCode>%s</branchCode>
                         <branch>%s</branch>
                         <currency>%s</currency>
                         <accGl>%s</accGl>
                         <cifNo>%s</cifNo>
                         <accountDetails>
                            <fullName>%s</fullName>
                            <economicSector>%s</economicSector>
                            <subEconomicSector>%s</subEconomicSector>
                            <addressesList>
                               <address>
                                  <lineNo>0</lineNo>
                                  <printStatement>0</printStatement>
                                  <contactNameEn>%s</contactNameEn>
                                  <addressEn1>%s</addressEn1>
                                  <addressEn2>%s</addressEn2>
                                  <cityEn>%s</cityEn>
                                  <countryEn>586</countryEn>
                                  <homeTel>%s</homeTel>
                                  <email>%s</email>
                                  <postalZipCode>%s</postalZipCode>
                                  <defaultAddress>Y</defaultAddress>
                                  <permanentAddress>Y</permanentAddress>
                               </address>
                            </addressesList>
                         </accountDetails>
                         <pointDetails><accountsign>C</accountsign></pointDetails>
                         %s
                         %s
                      </gen:createGeneralAccount>
                   </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(
                e(imal.getCompanyCode()), e(imal.getBranchCode()), e(imal.getBranchCode()), e(imal.getCurrency()),
                e(accGl), e(cifNo), e(fullName), e(econ), e(sub), e(fullName), e(addr1), e(addr2), e(city),
                e(tel), e(email), e(zip), requesterContext(true), vendorContext());
    }

    private String buildAuthorizeGeneralAccount(String additionalRef) {
        var imal = props.getImal();
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:gen="generalAccountsWs">
                   <soapenv:Header/>
                   <soapenv:Body>
                      <gen:authorizeGeneralAccount>
                         <serviceContext>
                            <businessArea>Retail</businessArea>
                            <businessDomain>Products</businessDomain>
                            <operationName>authorizeGeneralAccount</operationName>
                            <serviceDomain>GeneralAccounts</serviceDomain>
                            <serviceID>809</serviceID>
                            <version>1.0</version>
                         </serviceContext>
                         <companyCode>%s</companyCode>
                         <branchCode>%s</branchCode>
                         <waiveCharges>0</waiveCharges>
                         <account><additionalRef>%s</additionalRef></account>
                         %s
                         %s
                      </gen:authorizeGeneralAccount>
                   </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(e(imal.getCompanyCode()), e(imal.getBranchCode()), e(additionalRef),
                requesterContext(true), vendorContext());
    }

    private String buildCreateTransfer(String from, String to, BigDecimal amount, String instructions) {
        var imal = props.getImal();
        String today = java.time.LocalDate.now().toString();
        String amt = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tran="transferWs">
                   <soapenv:Header/>
                   <soapenv:Body>
                      <tran:createTransfer>
                         <serviceContext>
                            <businessArea>Retail</businessArea>
                            <businessDomain>PaymentsOperationsManagement</businessDomain>
                            <operationName>createTransfer</operationName>
                            <serviceDomain>Transfer</serviceDomain>
                            <serviceID>4801</serviceID>
                            <version>1.0</version>
                         </serviceContext>
                         <companyCode>%s</companyCode>
                         <branchCode>%s</branchCode>
                         <transactionType>%s</transactionType>
                         <forcePost>0</forcePost>
                         <useCardAccountNumber>0</useCardAccountNumber>
                         <fromAccount><additionalRef>%s</additionalRef></fromAccount>
                         <toAccounts>
                            <multiAccount>
                               <account><additionalRef>%s</additionalRef></account>
                               <amount>%s</amount>
                               <instructions1>%s</instructions1>
                               <instructions2></instructions2>
                            </multiAccount>
                         </toAccounts>
                         <transactionAmount>%s</transactionAmount>
                         <currencyIso>%s</currencyIso>
                         <transactionDate>%s</transactionDate>
                         <valueDate>%s</valueDate>
                         <useDate>1</useDate>
                         <differentTradeValueDate>0</differentTradeValueDate>
                         <cardPresent>0</cardPresent>
                         <transactionStatus>1</transactionStatus>
                         <transactionAlert>0</transactionAlert>
                         <compareDate>0</compareDate>
                         <useAccount>1</useAccount>
                         <checkBalance>1</checkBalance>
                         <useToAccount>1</useToAccount>
                         %s
                         %s
                      </tran:createTransfer>
                   </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(
                e(imal.getCompanyCode()), e(imal.getBranchCode()), e(imal.getTransferType()),
                e(from), e(to), e(amt), e(instructions != null ? instructions : "IFT"),
                e(amt), e(imal.getCurrency()), today, today,
                requesterContext(true), vendorContext());
    }

    private String requesterContext(boolean withChannel) {
        var imal = props.getImal();
        String ts = OffsetDateTime.now(ZoneOffset.ofHours(5)).format(TS);
        if (withChannel) {
            return """
                    <requesterContext>
                       <channelID>%s</channelID>
                       <hashKey>%s</hashKey>
                       <langId>%s</langId>
                       <password>%s</password>
                       <requesterTimeStamp>%s</requesterTimeStamp>
                       <userID>%s</userID>
                    </requesterContext>
                    """.formatted(e(imal.getChannelId()), e(imal.getHashKey()), e(imal.getLangId()),
                    e(imal.getPassword()), e(ts), e(imal.getUserId()));
        }
        return """
                <requesterContext>
                   <langId>%s</langId>
                   <password>%s</password>
                   <requesterTimeStamp>%s</requesterTimeStamp>
                   <userID>%s</userID>
                </requesterContext>
                """.formatted(e(imal.getLangId()), e(imal.getPassword()), e(ts), e(imal.getUserId()));
    }

    private String vendorContext() {
        return """
                <vendorContext>
                   <license>Copyright 2018 Azentio. All Rights Reserved</license>
                   <providerCompanyName>Azentio</providerCompanyName>
                   <providerID>IMAL</providerID>
                </vendorContext>
                """;
    }

    private void syncCustomer(String cnic, String cifNo, Map<String, Object> cifDetails) {
        String fullName = str(cifDetails, "fullName", "CUSTOMER");
        String mobile = str(cifDetails, "mobile", null);
        String email = str(cifDetails, "email", null);
        var existing = jdbc.queryForList("SELECT id FROM customers WHERE cnic=? AND deleted_at IS NULL", cnic);
        if (existing.isEmpty()) {
            jdbc.update("""
                    INSERT INTO customers(id, cnic, cif_no, full_name, mobile, email, status)
                    VALUES (?,?,?,?,?,?,'ACTIVE')
                    """, UUID.randomUUID().toString(), cnic, cifNo, fullName, mobile, email);
        } else {
            jdbc.update("""
                    UPDATE customers SET cif_no=?, full_name=COALESCE(?, full_name),
                      mobile=COALESCE(?, mobile), email=COALESCE(?, email) WHERE id=?
                    """, cifNo, fullName, mobile, email, existing.get(0).get("id"));
        }
    }

    private void syncAccount(String cifNo, String accountNumber, String iban, String fullName,
                             String productCode, String accGl) {
        var cust = jdbc.queryForList("SELECT id FROM customers WHERE cif_no=? AND deleted_at IS NULL", cifNo);
        if (cust.isEmpty()) {
            log.warn("syncAccount: no local customer for cif {}", cifNo);
            return;
        }
        String customerId = String.valueOf(cust.get(0).get("id"));
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE account_number=?", Integer.class, accountNumber);
        if (exists != null && exists > 0) {
            jdbc.update("UPDATE accounts SET status='ACTIVE', cif_no=?, acc_gl=?, product_code=? WHERE account_number=?",
                    cifNo, accGl, productCode != null ? productCode : "ASAAN_DIGITAL", accountNumber);
            return;
        }
        String ibanVal = iban != null && !iban.isBlank()
                ? iban
                : "PK00" + props.getBank().getIbanCode() + accountNumber;
        String acctId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO accounts(id, customer_id, account_number, iban, account_title, account_type,
                  product_code, acc_gl, cif_no, branch_code, currency, status)
                VALUES (?,?,?,?,?,'CURRENT',?,?,?,?,?,'ACTIVE')
                """, acctId, customerId, accountNumber, ibanVal, fullName,
                productCode != null ? productCode : "ASAAN_DIGITAL", accGl, cifNo,
                props.getBank().getBranchDefault(), "PKR");
        jdbc.update("""
                INSERT INTO ledger_balances(id, account_id, available_balance, ledger_balance, currency)
                VALUES (?,?,0,0,'PKR')
                """, UUID.randomUUID().toString(), acctId);
    }

    private static String e(String v) {
        return ImalSoapClient.esc(v);
    }

    private static String str(Map<String, Object> m, String key, String def) {
        if (m == null || m.get(key) == null) return def;
        String v = String.valueOf(m.get(key));
        return v.isBlank() || "null".equals(v) ? def : v;
    }
}
