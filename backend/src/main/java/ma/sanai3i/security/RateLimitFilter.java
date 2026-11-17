package ma.sanai3i.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Limits sensitive endpoints per IP: login, sign-up, SMS codes, password reset, uploads, reports, chat. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final long MIN = 60_000L;

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    /**
     * The proxy headers are resolved by Tomcat (server.forward-headers-strategy=native) from trusted proxies only.
     * Reading X-Forwarded-For here would let anyone pick a new "IP" per request and skip every limit.
     */
    public static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            String p = request.getRequestURI();
            String ip = clientIp(request);
            boolean ok = true;
            if (p.equals("/api/auth/login")) {
                ok = limiter.allow("login:" + ip, 20, 10 * MIN);
            } else if (p.equals("/api/auth/register") || p.equals("/api/auth/google")) {
                ok = limiter.allow("register:" + ip, 15, 60 * MIN);
            } else if (p.equals("/api/auth/otp")) {
                ok = limiter.allow("otp:" + ip, 6, 60 * MIN);
            } else if (p.equals("/api/auth/reset")) {
                ok = limiter.allow("reset:" + ip, 10, 60 * MIN);
            } else if (p.equals("/api/uploads")) {
                ok = limiter.allow("upload:" + ip, 60, 10 * MIN);
            } else if (p.equals("/api/reports")) {
                ok = limiter.allow("report:" + ip, 15, 60 * MIN);
            } else if (p.matches("/api/requests/\\d+/messages")) {
                ok = limiter.allow("chat:" + ip, 40, MIN);
            } else if (p.equals("/api/requests")) {
                ok = limiter.allow("newreq:" + ip, 20, 60 * MIN);
            } else if (p.matches("/api/requests/\\d+/offers")) {
                ok = limiter.allow("offer:" + ip, 60, 10 * MIN);
            } else if (p.matches("/api/requests/\\d+/job/.*")) {
                ok = limiter.allow("job:" + ip, 60, 10 * MIN);
            }
            if (!ok) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setHeader("Retry-After", "60");
                response.getWriter().write("{\"code\":\"RATE_LIMITED\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
