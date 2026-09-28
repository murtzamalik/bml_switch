package com.bankalmurqarmah.switchapp.lookups.service;

import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class LookupsService {
    private final JdbcTemplate jdbc;
    private final SwitchProperties props;

    public LookupsService(JdbcTemplate jdbc, SwitchProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public ResponseEntity<Map<String, Object>> banks(boolean active, Boolean supportsIbft, String q, String cursor,
                                                     int limit, String ifNoneMatch, String corr, HttpServletResponse response) {
        limit = Math.min(Math.max(limit, 1), 200);
        StringBuilder sql = new StringBuilder("""
                SELECT imd, bank_code as bankCode, short_name as bankShortName, legal_name as bankName,
                       iban_bank_code as ibanBankCode, supports_ibft as supportsIbft, is_active as isActive,
                       logo_url as logoUrl, sort_order as sortOrder
                FROM ref_banks WHERE deleted_at IS NULL
                """);
        List<Object> args = new ArrayList<>();
        if (active) {
            sql.append(" AND is_active=1");
        }
        if (supportsIbft != null) {
            sql.append(" AND supports_ibft=?");
            args.add(supportsIbft ? 1 : 0);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (legal_name LIKE ? OR short_name LIKE ? OR imd LIKE ?)");
            String like = "%" + q + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY sort_order ASC, legal_name ASC LIMIT ?");
        args.add(limit);
        List<Map<String, Object>> items = jdbc.queryForList(sql.toString(), args.toArray());
        normalizeBools(items, "supportsIbft", "isActive");
        String etag = "\"" + DigestUtils.md5DigestAsHex((catalogVersion() + items.size() + active).getBytes(StandardCharsets.UTF_8)) + "\"";
        response.setHeader("ETag", etag);
        response.setHeader("Cache-Control", "private, max-age=" + props.getLookups().getCacheMaxAgeSeconds() + ", must-revalidate");
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        Map<String, Object> body = envelope(items, corr);
        body.put("nextCursor", null);
        return ResponseEntity.ok(body);
    }

    public ResponseEntity<?> bankByImd(String imd, String corr) {
        var rows = jdbc.queryForList("""
                SELECT imd, bank_code as bankCode, short_name as bankShortName, legal_name as bankName,
                       iban_bank_code as ibanBankCode, supports_ibft as supportsIbft, is_active as isActive,
                       logo_url as logoUrl, sort_order as sortOrder
                FROM ref_banks WHERE imd=? AND deleted_at IS NULL AND is_active=1
                """, imd);
        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "title", "Not Found", "status", 404, "detail", "Unknown IMD", "correlationId", corr));
        }
        normalizeBools(rows, "supportsIbft", "isActive");
        Map<String, Object> body = envelope(List.of(), corr);
        body.putAll(rows.get(0));
        body.remove("items");
        return ResponseEntity.ok(body);
    }

    public Map<String, Object> simpleList(String table, String columns, String corr) {
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT " + columns + " FROM " + table + " WHERE is_active=1");
        normalizeBools(items, "isActive");
        return envelope(items, corr);
    }

    public Map<String, Object> orderedSteps(String corr) {
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT step_id as stepId, label, ordinal, is_active as isActive FROM ref_onboarding_steps WHERE is_active=1 ORDER BY ordinal");
        normalizeBools(items, "isActive");
        return envelope(items, corr);
    }

    public Map<String, Object> appConfig(String corr) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT config_key, config_value FROM ref_app_config WHERE is_public=1 AND is_active=1");
        Map<String, Object> items = new LinkedHashMap<>();
        for (var row : rows) {
            items.put(String.valueOf(row.get("config_key")), row.get("config_value"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("correlationId", corr);
        body.put("catalogVersion", Integer.parseInt(catalogVersion()));
        body.put("asOf", Instant.now().toString());
        body.put("items", items);
        body.put("Response_Code", "00");
        body.put("Response_Desc", "Success");
        return body;
    }

    public Map<String, Object> version(String corr) {
        return Map.of(
                "catalogVersion", catalogVersion(),
                "asOf", Instant.now().toString(),
                "correlationId", corr,
                "Response_Code", "00",
                "Response_Desc", "Success");
    }

    private Map<String, Object> envelope(List<Map<String, Object>> items, String corr) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("correlationId", corr);
        body.put("catalogVersion", Integer.parseInt(catalogVersion()));
        body.put("asOf", Instant.now().toString());
        body.put("items", items);
        body.put("Response_Code", "00");
        body.put("Response_Desc", "Success");
        return body;
    }

    private String catalogVersion() {
        try {
            return jdbc.queryForObject(
                    "SELECT config_value FROM ref_app_config WHERE config_key='lookupsVersion'", String.class);
        } catch (Exception e) {
            return "3";
        }
    }

    private void normalizeBools(List<Map<String, Object>> items, String... keys) {
        for (var item : items) {
            for (String key : keys) {
                Object v = item.get(key);
                if (v instanceof Number n) {
                    item.put(key, n.intValue() == 1);
                }
            }
        }
    }
}
