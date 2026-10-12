package com.tpm.auth;

import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tpm.auth.AuthDtos.AuthResponse;
import com.tpm.auth.AuthDtos.LoginRequest;
import com.tpm.auth.AuthDtos.RegisterRequest;
import com.tpm.config.AppProperties;
import com.tpm.seed.DemoAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Login, registration and token refresh")
public class AuthController {

    static final String REFRESH_COOKIE = "tpm_refresh";

    private final AuthService auth;
    private final AppProperties props;

    public AuthController(AuthService auth, AppProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account and sign in")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return withCookie(auth.register(req), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email & password",
            description = "Demo accounts: admin@demo.tpm, alice@demo.tpm, bob@demo.tpm, carol@demo.tpm, "
                    + "victor@demo.tpm (viewer) - password Demo@1234")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return withCookie(auth.login(req), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh-token cookie for a new access token (rotates the cookie)")
    public ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        try {
            return withCookie(auth.refresh(token), HttpStatus.OK);
        } catch (AuthenticationException e) {
            // Clear the bad cookie so the client stops retrying with it.
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
            problem.setTitle("Unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                    .body(problem);
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh-token family and clear the cookie")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        auth.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    @GetMapping("/demo-accounts")
    @Operation(summary = "Demo accounts available for one-click login")
    public List<DemoLogin> demoAccounts() {
        if (!props.seed().enabled()) return List.of();
        return DemoAccount.ALL.stream()
                .map(a -> new DemoLogin(a.email(), a.fullName(), a.description(), a.color(), DemoAccount.PASSWORD))
                .toList();
    }

    public record DemoLogin(String email, String fullName, String description, String color, String password) {}

    private ResponseEntity<AuthResponse> withCookie(AuthService.Session session, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), props.jwt().refreshTokenTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(session.response());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.cookie().secure())
                .sameSite(props.cookie().sameSite())
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
