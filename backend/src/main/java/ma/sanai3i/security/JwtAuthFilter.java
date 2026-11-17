package ma.sanai3i.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/** Not a Spring bean on purpose: SecurityConfig adds it to the chain, so it is not registered twice. */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final BlockList blocked;

    public JwtAuthFilter(JwtService jwt, BlockList blocked) {
        this.jwt = jwt;
        this.blocked = blocked;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Optional<AuthUser> parsed = jwt.parse(header.substring(7));
            if (parsed.isPresent()) {
                AuthUser user = parsed.get();
                if (blocked.isBlocked(user.userId())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write("{\"code\":\"ACCOUNT_BLOCKED\"}");
                    return;
                }
                if (!blocked.tokenValid(user.userId(), user.tokenVersion())) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write("{\"code\":\"SESSION\"}");
                    return;
                }
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
