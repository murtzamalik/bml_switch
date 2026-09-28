package com.bankalmurqarmah.switchapp.shared.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SeedPasswordFixer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public SeedPasswordFixer(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String clientHash = encoder.encode("change_me_sandbox_secret");
        String userHash = encoder.encode("Sandbox@123");
        jdbc.update("UPDATE api_clients SET client_secret_hash = ? WHERE client_code = ?",
                clientHash, "appinsnap-sandbox");
        jdbc.update("UPDATE customers SET password_hash = ? WHERE username = ?",
                userHash, "ali.khan");
    }
}
