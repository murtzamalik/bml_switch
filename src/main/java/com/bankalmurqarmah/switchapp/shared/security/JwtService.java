package com.bankalmurqarmah.switchapp.shared.security;

import com.bankalmurqarmah.switchapp.shared.config.SwitchProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private final SwitchProperties props;

    public JwtService(SwitchProperties props) {
        this.props = props;
    }

    private SecretKey key() {
        byte[] bytes = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            bytes = (props.getJwt().getSecret() + "________________________").substring(0, 32).getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String createChannelAccessToken(String clientId, String clientCode) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getJwt().getAccessTtlSeconds());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(clientCode)
                .claims(Map.of("typ", "channel", "clientId", clientId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key())
                .compact();
    }

    public String createCustomerToken(String customerId, String cnic, String username) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getJwt().getAccessTtlSeconds());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username != null ? username : cnic)
                .claims(Map.of("typ", "customer", "customerId", customerId, "cnic", cnic))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    public boolean isChannel(Claims claims) {
        return "channel".equals(String.valueOf(claims.get("typ")));
    }

    public boolean isCustomer(Claims claims) {
        return "customer".equals(String.valueOf(claims.get("typ")));
    }
}
