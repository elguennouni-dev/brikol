package ma.sanai3i.security;

import ma.sanai3i.domain.Role;

/** The authenticated caller, read from the JWT. Injected with @AuthenticationPrincipal. */
public record AuthUser(Long userId, Role role, int tokenVersion, String tokenId, java.time.Instant expiresAt) {
    public AuthUser(Long userId, Role role) {
        this(userId, role, 0, null, null);
    }

    public AuthUser(Long userId, Role role, int tokenVersion) {
        this(userId, role, tokenVersion, null, null);
    }
}
