package com.tpm.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly typed view over the {@code app.*} configuration tree. */
@ConfigurationProperties("app")
public record AppProperties(
        String frontendUrl,
        List<String> corsAllowedOrigins,
        Jwt jwt,
        Cookie cookie,
        Mail mail,
        RateLimit rateLimit,
        Seed seed) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record Cookie(boolean secure, String sameSite) {}

    public record Mail(boolean enabled, String from) {}

    public record RateLimit(int capacity, int refillPerMinute) {}

    public record Seed(boolean enabled) {}
}
