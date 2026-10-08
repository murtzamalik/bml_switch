package com.bankalmurqarmah.switchapp.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "switch")
public class SwitchProperties {
    private Jwt jwt = new Jwt();
    private Auth auth = new Auth();
    private Imal imal = new Imal();
    private Otp otp = new Otp();
    private Profile profile = new Profile();
    private Bank bank = new Bank();
    private Asaan asaan = new Asaan();
    private Lookups lookups = new Lookups();
    private String appId = "MB";
    private String delimiter = "^";
    private String netId = "BML";
    private String merchantName = "BankAlMurqarmah";
    private String mcc = "6012";
    private String channelId = "MOBILE";

    /** Static non-expiring API token for all switch APIs (Authorization: Bearer …). */
    public static class Auth {
        private String staticToken = "BML-POC-STATIC-TOKEN-2026-AIS";
        public String getStaticToken() { return staticToken; }
        public void setStaticToken(String staticToken) { this.staticToken = staticToken; }
    }

    public static class Jwt {
        private String secret = "change_me_min_32_chars_256bit_entropy____";
        private long accessTtlSeconds = 900;
        private long refreshTtlSeconds = 604800;
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getAccessTtlSeconds() { return accessTtlSeconds; }
        public void setAccessTtlSeconds(long accessTtlSeconds) { this.accessTtlSeconds = accessTtlSeconds; }
        public long getRefreshTtlSeconds() { return refreshTtlSeconds; }
        public void setRefreshTtlSeconds(long refreshTtlSeconds) { this.refreshTtlSeconds = refreshTtlSeconds; }
    }
    public static class Imal {
        /** false = SoapImalAdapter (live open+IFT); list/balance still local DB. */
        private boolean mock = false;
        private String companyCode = "1";
        private String branchCode = "209";
        private String currency = "586";
        private String cifType = "1";
        private String idType = "1";
        private String economicSector = "111";
        private String subEconomicSector = "3456";
        private String userId = "MODEL.B";
        private String password = "MTIz";
        private String channelId = "1";
        private String hashKey = "1";
        private String langId = "EN";
        private String transferType = "402";
        private int timeoutMs = 30000;
        private Soap soap = new Soap();
        public boolean isMock() { return mock; }
        public void setMock(boolean mock) { this.mock = mock; }
        public String getCompanyCode() { return companyCode; }
        public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }
        public String getBranchCode() { return branchCode; }
        public void setBranchCode(String branchCode) { this.branchCode = branchCode; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getCifType() { return cifType; }
        public void setCifType(String cifType) { this.cifType = cifType; }
        public String getIdType() { return idType; }
        public void setIdType(String idType) { this.idType = idType; }
        public String getEconomicSector() { return economicSector; }
        public void setEconomicSector(String economicSector) { this.economicSector = economicSector; }
        public String getSubEconomicSector() { return subEconomicSector; }
        public void setSubEconomicSector(String subEconomicSector) { this.subEconomicSector = subEconomicSector; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getChannelId() { return channelId; }
        public void setChannelId(String channelId) { this.channelId = channelId; }
        public String getHashKey() { return hashKey; }
        public void setHashKey(String hashKey) { this.hashKey = hashKey; }
        public String getLangId() { return langId; }
        public void setLangId(String langId) { this.langId = langId; }
        public String getTransferType() { return transferType; }
        public void setTransferType(String transferType) { this.transferType = transferType; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public Soap getSoap() { return soap; }
        public void setSoap(Soap soap) { this.soap = soap; }
    }

    public static class Soap {
        private String cifUrl = "http://10.2.60.66:7005/imal_core_cpws_imal_cif/pathservices/";
        private String accountUrl = "http://10.2.60.66:7005/imal_core_cpws_imal_acc/pathservices/";
        private String transferUrl = "http://10.2.60.66:7005/imal_core_cpws_imal_trn/pathservices/";
        public String getCifUrl() { return cifUrl; }
        public void setCifUrl(String cifUrl) { this.cifUrl = cifUrl; }
        public String getAccountUrl() { return accountUrl; }
        public void setAccountUrl(String accountUrl) { this.accountUrl = accountUrl; }
        public String getTransferUrl() { return transferUrl; }
        public void setTransferUrl(String transferUrl) { this.transferUrl = transferUrl; }
    }
    public static class Otp {
        private boolean mock = true;
        private String fixedCode = "1234";
        public boolean isMock() { return mock; }
        public void setMock(boolean mock) { this.mock = mock; }
        public String getFixedCode() { return fixedCode; }
        public void setFixedCode(String fixedCode) { this.fixedCode = fixedCode; }
    }
    public static class Profile {
        private boolean sandbox = true;
        public boolean isSandbox() { return sandbox; }
        public void setSandbox(boolean sandbox) { this.sandbox = sandbox; }
    }
    public static class Bank {
        private String imd = "627000";
        private String ibanCode = "BMAL";
        private String branchDefault = "001";
        private String agentAccount = "0000000000";
        public String getImd() { return imd; }
        public void setImd(String imd) { this.imd = imd; }
        public String getIbanCode() { return ibanCode; }
        public void setIbanCode(String ibanCode) { this.ibanCode = ibanCode; }
        public String getBranchDefault() { return branchDefault; }
        public void setBranchDefault(String branchDefault) { this.branchDefault = branchDefault; }
        public String getAgentAccount() { return agentAccount; }
        public void setAgentAccount(String agentAccount) { this.agentAccount = agentAccount; }
    }
    public static class Asaan {
        private BigDecimal maxBalance = new BigDecimal("1000000");
        private BigDecimal dailyDebitLimit = new BigDecimal("200000");
        public BigDecimal getMaxBalance() { return maxBalance; }
        public void setMaxBalance(BigDecimal maxBalance) { this.maxBalance = maxBalance; }
        public BigDecimal getDailyDebitLimit() { return dailyDebitLimit; }
        public void setDailyDebitLimit(BigDecimal dailyDebitLimit) { this.dailyDebitLimit = dailyDebitLimit; }
    }
    public static class Lookups {
        private int cacheMaxAgeSeconds = 300;
        public int getCacheMaxAgeSeconds() { return cacheMaxAgeSeconds; }
        public void setCacheMaxAgeSeconds(int cacheMaxAgeSeconds) { this.cacheMaxAgeSeconds = cacheMaxAgeSeconds; }
    }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }
    public Imal getImal() { return imal; }
    public void setImal(Imal imal) { this.imal = imal; }
    public Otp getOtp() { return otp; }
    public void setOtp(Otp otp) { this.otp = otp; }
    public Profile getProfile() { return profile; }
    public void setProfile(Profile profile) { this.profile = profile; }
    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }
    public Asaan getAsaan() { return asaan; }
    public void setAsaan(Asaan asaan) { this.asaan = asaan; }
    public Lookups getLookups() { return lookups; }
    public void setLookups(Lookups lookups) { this.lookups = lookups; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getDelimiter() { return delimiter; }
    public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
    public String getNetId() { return netId; }
    public void setNetId(String netId) { this.netId = netId; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getMcc() { return mcc; }
    public void setMcc(String mcc) { this.mcc = mcc; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
}
