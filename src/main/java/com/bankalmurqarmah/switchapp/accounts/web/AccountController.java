package com.bankalmurqarmah.switchapp.accounts.web;

import com.bankalmurqarmah.switchapp.accounts.service.AccountService;
import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Existing controller rewritten per plan.
 * Callers: AIS via POST /api/v1/account/open and /account-list (channel JWT).
 * User instruction: Implement the iMal Account Open + Existing Registration Plan.
 */
@RestController
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/api/v1/account/account-list")
    public Map<String, Object> accountList(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.accountList(body, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/open")
    public Map<String, Object> open(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.open(body, CorrelationIds.resolve(request));
    }
}
