package com.bankalmurqarmah.switchapp.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "switch")
public class SwitchProperties {
    private Jwt jwt = new Jwt();
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
        private boolean mock = true;
        public boolean isMock() { return mock; }
        public void setMock(boolean mock) { this.mock = mock; }
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
