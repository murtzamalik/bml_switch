package com.bankalmurqarmah.switchapp.system;

import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import com.bankalmurqarmah.switchapp.shared.kernel.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final SwitchProperties props;
    private final JdbcTemplate jdbc;

    public SystemController(SwitchProperties props, JdbcTemplate jdbc) {
        this.props = props;
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Map<String, Object> health(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("mockImal", props.getImal().isMock());
        body.put("mockOtp", props.getOtp().isMock());
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            body.put("db", "UP");
        } catch (Exception e) {
            body.put("status", "DOWN");
            body.put("db", "DOWN");
        }
        body.put("correlationId", CorrelationIds.resolve(request));
        return body;
    }

    @GetMapping("/version")
    public Map<String, Object> version(HttpServletRequest request) {
        String catalog = "3";
        try {
            catalog = jdbc.queryForObject(
                    "SELECT config_value FROM ref_app_config WHERE config_key='lookupsVersion' AND is_active=1",
                    String.class);
        } catch (Exception ignored) {
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appVersion", "0.1.0-SNAPSHOT");
        body.put("catalogVersion", catalog);
        body.put("mockImal", props.getImal().isMock());
        body.put("correlationId", CorrelationIds.resolve(request));
        return body;
    }
}
