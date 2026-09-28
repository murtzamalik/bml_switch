package com.bankalmurqarmah.switchapp.accounts.web;

import com.bankalmurqarmah.switchapp.accounts.service.AccountService;
import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/api/v1/account/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.login(body, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.register(body, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/customer-detail")
    public Map<String, Object> customerDetail(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.customerDetail(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/account-list")
    public Map<String, Object> accountList(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.accountList(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/open")
    public Map<String, Object> open(@RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idem,
                                    HttpServletRequest request) {
        return accountService.open(body, idem, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/upload-documents-unikrew")
    public Map<String, Object> upload(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.uploadUnikrew(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/validate-document-unikrew")
    public Map<String, Object> validateDoc(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.validateUnikrew(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/verify-liveliness")
    public Map<String, Object> liveliness(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.liveliness(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/verify-fingers")
    public Map<String, Object> fingers(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.fingers(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/approve-mock")
    public Map<String, Object> approveMock(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.approveMock(body, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/change-password")
    public Map<String, Object> changePassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.changePassword(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/account/reset-password-mock")
    public Map<String, Object> resetPassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.resetPasswordMock(body, CorrelationIds.resolve(request));
    }

    @PostMapping("/api/v1/accounts/information")
    public Map<String, Object> information(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return accountService.information(body, request, CorrelationIds.resolve(request));
    }
}
