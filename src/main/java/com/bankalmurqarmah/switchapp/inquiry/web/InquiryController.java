package com.bankalmurqarmah.switchapp.inquiry.web;

import com.bankalmurqarmah.switchapp.inquiry.service.InquiryService;
import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/inquiry")
public class InquiryController {
    private final InquiryService inquiryService;

    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @PostMapping("/balance")
    public Map<String, Object> balance(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return inquiryService.balance(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/ift-title")
    public Map<String, Object> iftTitle(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return inquiryService.iftTitle(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/ibft-title")
    public Map<String, Object> ibftTitle(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return inquiryService.ibftTitle(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/cnic")
    public Map<String, Object> cnic(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return inquiryService.cnic(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/ibft-status")
    public Map<String, Object> ibftStatus(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return inquiryService.ibftStatus(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/mini-statement")
    public Map<String, Object> mini(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return inquiryService.miniStatement(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/statement")
    public Map<String, Object> statement(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return inquiryService.statement(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/limits")
    public Map<String, Object> limits(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return inquiryService.limits(body, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/receipt")
    public Map<String, Object> receipt(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return inquiryService.receipt(body, request, CorrelationIds.resolve(request));
    }
}
