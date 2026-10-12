package com.tpm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.tpm.auth.AuthDtos.LoginRequest;
import com.tpm.config.AppProperties;
import com.tpm.user.User;
import com.tpm.user.UserRepository;

class AuthServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtEncoder jwtEncoder = mock(JwtEncoder.class);
    private AuthService service;
    private User alice;

    @BeforeEach
    void setUp() {
        var props = new AppProperties("http://localhost", java.util.List.of(),
                new AppProperties.Jwt("c2VjcmV0", "tpm", Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.Cookie(false, "Lax"), new AppProperties.Mail(false, "x"),
                new AppProperties.RateLimit(10, 10), new AppProperties.Seed(true));
        when(encoder.encode(any())).thenReturn("hash");
        service = new AuthService(users, tokens, encoder, jwtEncoder, props);
        alice = new User("alice@demo.tpm", "hash", "Alice", User.Role.USER, "#000");
        ReflectionTestUtils.setField(alice, "id", 1L);
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("jwt").header("alg", "HS256")
                .claim("sub", "1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());
    }

    @Test
    void loginWithWrongPasswordFailsWithGenericMessage() {
        when(users.findByEmail("alice@demo.tpm")).thenReturn(Optional.of(alice));
        when(encoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("Alice@Demo.tpm", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void unknownEmailStillRunsPasswordHashToPreventTimingAttacks() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@x.io", "pw")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(encoder).matches(eq("pw"), any());
    }

    @Test
    void disabledUserCannotLogIn() {
        alice.setEnabled(false);
        when(users.findByEmail("alice@demo.tpm")).thenReturn(Optional.of(alice));
        when(encoder.matches("pw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("alice@demo.tpm", "pw")))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void refreshRotatesTheToken() {
        var token = new RefreshToken(alice, AuthService.sha256("raw"), UUID.randomUUID(), Instant.now().plusSeconds(60));
        when(tokens.findByTokenHash(AuthService.sha256("raw"))).thenReturn(Optional.of(token));

        var session = service.refresh("raw");

        assertThat(token.isRevoked()).isTrue();
        assertThat(session.refreshToken()).isNotEqualTo("raw");
        assertThat(session.response().accessToken()).isEqualTo("jwt");
    }

    @Test
    void reusingARotatedTokenAfterTheGraceWindowRevokesTheWholeFamily() {
        var family = UUID.randomUUID();
        var token = new RefreshToken(alice, AuthService.sha256("stolen"), family, Instant.now().plusSeconds(3600));
        token.rotate();
        ReflectionTestUtils.setField(token, "revokedAt", Instant.now().minusSeconds(60));
        when(tokens.findByTokenHash(AuthService.sha256("stolen"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh("stolen"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("reuse");
        verify(tokens).revokeFamily(eq(family), any());
    }

    @Test
    void concurrentRefreshWithinGraceWindowIsTolerated() {
        var token = new RefreshToken(alice, AuthService.sha256("raw"), UUID.randomUUID(), Instant.now().plusSeconds(3600));
        token.rotate(); // just rotated by another tab
        when(tokens.findByTokenHash(AuthService.sha256("raw"))).thenReturn(Optional.of(token));

        var session = service.refresh("raw");

        assertThat(session.response().accessToken()).isEqualTo("jwt");
        verify(tokens, never()).revokeFamily(any(), any());
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        var token = new RefreshToken(alice, AuthService.sha256("old"), UUID.randomUUID(), Instant.now().minusSeconds(1));
        when(tokens.findByTokenHash(AuthService.sha256("old"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh("old")).isInstanceOf(BadCredentialsException.class);
    }
}
