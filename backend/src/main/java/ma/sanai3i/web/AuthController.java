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

    public AuthController(AuthService auth, OtpService otp) {
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
}
