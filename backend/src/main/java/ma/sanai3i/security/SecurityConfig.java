package ma.sanai3i.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {
    /**
     * Only our own scripts (no inline JavaScript anywhere), plus Google sign-in. Styles may be inline (React style
     * attributes). Maps: OpenStreetMap tiles and the Photon geocoder. Push: Firebase endpoints.
     */
    static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self' https://accounts.google.com/gsi/client",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://accounts.google.com/gsi/style",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data: blob: https://tile.openstreetmap.org https://*.tile.openstreetmap.org https://*.googleusercontent.com",
            "media-src 'self' blob:",
            "connect-src 'self' https://photon.komoot.io https://accounts.google.com/gsi/ https://*.googleapis.com",
            "frame-src https://accounts.google.com/gsi/",
            "worker-src 'self'",
            "manifest-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, BlockList blockList,
                                    TokenRevocations revocations) throws Exception {
        http
                // No cookies: the token travels in the Authorization header, which a third-party site cannot add,
                // so cross-site request forgery does not apply.
                .csrf(csrf -> csrf.disable())
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives(CSP))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // always sent: the public site is HTTPS only (Nginx redirects port 80), even if a proxy
                        // forgot X-Forwarded-Proto
                        .httpStrictTransportSecurity(t -> t.requestMatcher(org.springframework.security.web.util.matcher.AnyRequestMatcher.INSTANCE)
                                .includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .crossOriginOpenerPolicy(c -> c.policy(org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN_ALLOW_POPUPS))
                        .permissionsPolicyHeader(pp -> pp.policy("camera=(self), microphone=(self), geolocation=(self), payment=(), usb=()")))
                .cors(Customizer.withDefaults())
                // no form login, no /logout redirect, no saved requests: a pure token API
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .requestCache(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()   // live (SSE) responses
                        .requestMatchers("/api/auth/**", "/api/public/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())      // static web app, /error, health
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthFilter(jwtService, blockList, revocations), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Empty app.cors-origins means same-origin only, which is what the WebView apps use. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins:}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        if (!origins.isBlank()) {
            config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("*"));
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
