package ma.sanai3i.web;

import jakarta.validation.Valid;
import ma.sanai3i.dto.Dto.AuthResponse;
import ma.sanai3i.dto.Dto.GoogleAuthRequest;
import ma.sanai3i.dto.Dto.OtpRequest;
import ma.sanai3i.dto.Dto.ResetPasswordRequest;
import ma.sanai3i.service.OtpService;
import org.springframework.http.ResponseEntity;
import ma.sanai3i.dto.Dto.LoginRequest;
import ma.sanai3i.dto.Dto.RegisterRequest;
import ma.sanai3i.dto.Dto.UserDto;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    private final OtpService otp;

    private final ma.sanai3i.security.TokenRevocations revocations;

    public AuthController(AuthService auth, OtpService otp, ma.sanai3i.security.TokenRevocations revocations) {
        this.revocations = revocations;
        this.auth = auth;
        this.otp = otp;
    }

    /** Sends a 6-digit SMS code. purpose: REGISTER (new account) or RESET (forgotten password). */
    @PostMapping("/auth/otp")
    public ResponseEntity<Void> sendOtp(@Valid @RequestBody OtpRequest body) {
        otp.send(body.phone(), body.purpose(), body.lang());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/reset")
    public AuthResponse reset(@Valid @RequestBody ResetPasswordRequest body) {
        return auth.resetPassword(body);
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest body) {
        return auth.register(body);
    }

    @PostMapping("/auth/google")
    public AuthResponse google(@Valid @RequestBody GoogleAuthRequest body) {
        return auth.google(body);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest body) {
        return auth.login(body);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser me) {
        return auth.me(me);
    }

    /** Signs out this token (the app forgets it too). */
    @PostMapping("/auth/logout")
    public org.springframework.http.ResponseEntity<Void> logout(
            @org.springframework.security.core.annotation.AuthenticationPrincipal ma.sanai3i.security.AuthUser me) {
        auth.logout(me, revocations);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    /** Signs out every device of this account. */
    @PostMapping("/auth/logout-all")
    public org.springframework.http.ResponseEntity<Void> logoutAll(
            @org.springframework.security.core.annotation.AuthenticationPrincipal ma.sanai3i.security.AuthUser me) {
        if (me == null) {
            throw new ma.sanai3i.common.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        auth.logoutAll(me);
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}
