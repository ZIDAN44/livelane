package com.tpm.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.tpm.auth.AuthDtos.AuthResponse;
import com.tpm.auth.AuthDtos.LoginRequest;
import com.tpm.auth.AuthDtos.RegisterRequest;
import com.tpm.common.Exceptions.ConflictException;
import com.tpm.config.AppProperties;
import com.tpm.user.User;
import com.tpm.user.UserDtos.UserProfile;
import com.tpm.user.UserRepository;

/**
 * Authentication with short-lived access JWTs and rotating refresh tokens.
 *
 * <p>Refresh-token rotation with reuse detection (OAuth 2.0 Security BCP): every refresh returns
 * a new token and revokes the old one. If a <em>revoked</em> token is ever presented again, it was
 * stolen or replayed, so the entire token family is revoked and the user must log in again.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final List<String> AVATAR_COLORS =
            List.of("#6366f1", "#ec4899", "#14b8a6", "#f59e0b", "#8b5cf6", "#ef4444", "#0ea5e9", "#22c55e");

    private static final java.time.Duration REUSE_GRACE = java.time.Duration.ofSeconds(10);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();
    /** Used to equalise timing when the e-mail doesn't exist (prevents user enumeration). */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder, AppProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.dummyHash = passwordEncoder.encode("timing-equaliser");
    }

    /** Result of a successful login / refresh: API response + raw refresh token for the cookie. */
    public record Session(AuthResponse response, String refreshToken) {}

    @Transactional
    public Session register(RegisterRequest req) {
        var email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw new ConflictException("An account with this email already exists");
        var color = AVATAR_COLORS.get(Math.floorMod(email.hashCode(), AVATAR_COLORS.size()));
        var user = users.save(new User(email, passwordEncoder.encode(req.password()), req.fullName(), User.Role.USER, color));
        return issue(user, UUID.randomUUID());
    }

    @Transactional
    public Session login(LoginRequest req) {
        var user = users.findByEmail(req.email().trim().toLowerCase()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw new BadCredentialsException("Invalid email or password");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        if (!user.isEnabled()) throw new DisabledException("This account has been disabled");
        return issue(user, UUID.randomUUID());
    }

    /** noRollbackFor: the family revocation on token reuse must be committed even though we throw. */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Session refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new BadCredentialsException("Missing refresh token");
        var token = refreshTokens.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (token.rotatedWithin(REUSE_GRACE, Instant.now()) && token.getUser().isEnabled()) {
            return issue(token.getUser(), token.getFamilyId()); // concurrent refresh from another tab
        }
        if (token.isRevoked()) {
            log.warn("Refresh token reuse detected for user {} - revoking family {}", token.getUser().getId(),
                    token.getFamilyId());
            refreshTokens.revokeFamily(token.getFamilyId(), Instant.now());
            throw new BadCredentialsException("Refresh token reuse detected; please sign in again");
        }
        if (token.isExpired(Instant.now())) throw new BadCredentialsException("Refresh token expired");
        if (!token.getUser().isEnabled()) throw new DisabledException("This account has been disabled");
        token.rotate();
        return issue(token.getUser(), token.getFamilyId());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(sha256(rawToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void revokeAllSessions(Long userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    /** Housekeeping: drop tokens that expired more than a day ago. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int n = refreshTokens.deleteExpiredBefore(Instant.now().minus(1, ChronoUnit.DAYS));
        if (n > 0) log.info("Purged {} expired refresh tokens", n);
    }

    private Session issue(User user, UUID family) {
        var now = Instant.now();
        var ttl = props.jwt().accessTokenTtl();
        var claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("roles", List.of(user.getRole().name()))
                .claim("name", user.getFullName())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        var raw = newOpaqueToken();
        refreshTokens.save(new RefreshToken(user, sha256(raw), family, now.plus(props.jwt().refreshTokenTtl())));

        return new Session(new AuthResponse(accessToken, "Bearer", ttl.toSeconds(), UserProfile.of(user)), raw);
    }

    private String newOpaqueToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
