package ma.sanai3i.security;

import ma.sanai3i.domain.Role;

/** The authenticated caller, read from the JWT. Injected with @AuthenticationPrincipal. */
public record AuthUser(Long userId, Role role, int tokenVersion) {
    public AuthUser(Long userId, Role role) {
        this(userId, role, 0);
    }
}
