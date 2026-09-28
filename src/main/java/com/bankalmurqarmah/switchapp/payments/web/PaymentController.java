package com.bankalmurqarmah.switchapp.payments.web;

import com.bankalmurqarmah.switchapp.payments.service.PaymentService;
import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/ift")
    public Map<String, Object> ift(@RequestBody Map<String, Object> body,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idem,
                                   HttpServletRequest request) {
        return paymentService.ift(body, idem, request, CorrelationIds.resolve(request));
    }

    @PostMapping("/ibft")
    public Map<String, Object> ibft(@RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idem,
                                    HttpServletRequest request) {
        return paymentService.ibft(body, idem, request, CorrelationIds.resolve(request));
    }
}
