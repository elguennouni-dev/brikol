package ma.sanai3i.security;

/** Checks a Google ID token ("credential" returned by Sign in with Google). */
public interface GoogleVerifier {

    record GoogleIdentity(String sub, String email, boolean emailVerified, String name) {
    }

    /** @throws ma.sanai3i.common.ApiException 401 GOOGLE_INVALID when the token is not valid for this app */
    GoogleIdentity verify(String credential);
}
