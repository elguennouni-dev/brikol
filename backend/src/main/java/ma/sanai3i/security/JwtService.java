package ma.sanai3i.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtService {
    private final SecretKey key;
    private final long ttlMillis;

    static final String DEV_SECRET = "dev-only-secret-change-me-dev-only-secret-change-me";

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.ttl-days}") long ttlDays, Environment env) {
        boolean dev = java.util.Arrays.asList(env.getActiveProfiles()).contains("dev");
        if (!dev && (secret == null || secret.length() < 32 || DEV_SECRET.equals(secret))) {
            // anyone could sign tokens with the public default: never start like that in production
            throw new IllegalStateException("JWT_SECRET must be set to a random value of at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlDays * 24L * 60L * 60L * 1000L;
    }

    public String issue(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.id))
                .claim("role", user.role.name())
                .claim("tv", user.tokenVersion)
                .id(java.util.UUID.randomUUID().toString())      // lets one token be signed out (logout)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Long id = Long.valueOf(claims.getSubject());
            Role role = Role.valueOf(claims.get("role", String.class));
            Integer tv = claims.get("tv", Integer.class);
            Date exp = claims.getExpiration();
            return Optional.of(new AuthUser(id, role, tv == null ? 0 : tv, claims.getId(), exp == null ? null : exp.toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
