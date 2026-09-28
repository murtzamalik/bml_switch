package com.bankalmurqarmah.switchapp.lookups.web;

import com.bankalmurqarmah.switchapp.lookups.service.LookupsService;
import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/lookups")
public class LookupsController {
    private final LookupsService lookupsService;

    public LookupsController(LookupsService lookupsService) {
        this.lookupsService = lookupsService;
    }

    @GetMapping("/banks")
    public ResponseEntity<Map<String, Object>> banks(
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(required = false) Boolean supportsIbft,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "If-None-Match", required = false) String inm,
            HttpServletRequest request,
            HttpServletResponse response) {
        return lookupsService.banks(active, supportsIbft, q, cursor, limit, inm, CorrelationIds.resolve(request), response);
    }

    @GetMapping("/banks/{imd}")
    public ResponseEntity<?> bank(@PathVariable String imd, HttpServletRequest request) {
        return lookupsService.bankByImd(imd, CorrelationIds.resolve(request));
    }

    @GetMapping({"/purpose-of-payment", "/purpose-codes"})
    public Map<String, Object> purposePayment(HttpServletRequest request) {
        return lookupsService.simpleList("ref_purpose_codes", "code, description, applies_to as appliesTo, is_active as isActive",
                CorrelationIds.resolve(request));
    }

    @GetMapping("/purpose-of-account")
    public Map<String, Object> purposeAccount(HttpServletRequest request) {
        return lookupsService.simpleList("ref_purpose_of_account", "code, description, is_active as isActive",
                CorrelationIds.resolve(request));
    }

    @GetMapping("/occupations")
    public Map<String, Object> occupations(HttpServletRequest request) {
        return lookupsService.simpleList("ref_occupations", "code, description, is_active as isActive",
                CorrelationIds.resolve(request));
    }

    @GetMapping("/response-codes")
    public Map<String, Object> responseCodes(HttpServletRequest request) {
        return lookupsService.simpleList("ref_response_codes", "code, message_en as messageEn, severity, is_active as isActive",
                CorrelationIds.resolve(request));
    }

    @GetMapping("/account-types")
    public Map<String, Object> accountTypes(HttpServletRequest request) {
        return lookupsService.simpleList("ref_account_types", "code, label, is_active as isActive",
                CorrelationIds.resolve(request));
    }

    @GetMapping("/provinces")
    public Map<String, Object> provinces(HttpServletRequest request) {
        return lookupsService.simpleList("ref_provinces", "code, name_en as nameEn, sort_order as sortOrder, is_active as isActive",
                CorrelationIds.resolve(request));
    }

    @GetMapping("/id-types")
    public Map<String, Object> idTypes(HttpServletRequest request) {
        return lookupsService.simpleList("ref_id_types", "code, label, is_active as isActive",
                CorrelationIds.resolve(request));
    }

    @GetMapping("/finger-indexes")
    public Map<String, Object> fingers(HttpServletRequest request) {
        return lookupsService.simpleList("ref_finger_indexes", "index_code as indexCode, label, hand, sort_order as sortOrder, is_active as isActive",
                CorrelationIds.resolve(request));
    }

    @GetMapping("/onboarding-steps")
    public Map<String, Object> steps(HttpServletRequest request) {
        return lookupsService.orderedSteps(CorrelationIds.resolve(request));
    }

    @GetMapping("/branches")
    public Map<String, Object> branches(HttpServletRequest request) {
        return lookupsService.simpleList("ref_branches", "branch_code as branchCode, name, city, province_code as provinceCode, is_active as isActive",
                CorrelationIds.resolve(request));
    }

    @GetMapping("/currencies")
    public Map<String, Object> currencies(HttpServletRequest request) {
        return lookupsService.simpleList("ref_currencies", "iso_code as isoCode, numeric_code as numericCode, decimals, is_active as isActive",
                CorrelationIds.resolve(request));
    }

    @GetMapping("/app-config")
    public Map<String, Object> appConfig(HttpServletRequest request) {
        return lookupsService.appConfig(CorrelationIds.resolve(request));
    }

    @GetMapping("/version")
    public Map<String, Object> version(HttpServletRequest request) {
        return lookupsService.version(CorrelationIds.resolve(request));
    }
}
