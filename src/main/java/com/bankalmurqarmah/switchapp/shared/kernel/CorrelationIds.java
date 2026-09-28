package com.bankalmurqarmah.switchapp.shared.kernel;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public final class CorrelationIds {
    public static final String HEADER = "X-Correlation-Id";

    private CorrelationIds() {}

    public static String resolve(HttpServletRequest request) {
        String existing = request.getHeader(HEADER);
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        return UUID.randomUUID().toString();
    }
}
