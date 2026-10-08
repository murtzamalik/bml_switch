package com.bankalmurqarmah.switchapp.shared.kernel;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * Resolves/echoes X-Correlation-Id for API + SOAP log correlation on bank machines.
 * Callers: ApiAccessLoggingFilter, ChannelJwtFilter, controllers.
 * User: strong logging so errors catch without IDE; SOAP requests printable for SoapUI.
 */
public final class CorrelationIds {
    public static final String HEADER = "X-Correlation-Id";

    private CorrelationIds() {}

    public static String resolve(HttpServletRequest request) {
        Object attr = request.getAttribute(HEADER);
        if (attr != null && !String.valueOf(attr).isBlank()) {
            return String.valueOf(attr).trim();
        }
        String existing = request.getHeader(HEADER);
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        return UUID.randomUUID().toString();
    }
}
