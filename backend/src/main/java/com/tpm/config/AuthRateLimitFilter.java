package com.tpm.config;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tpm.common.TokenBucket;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-client-IP token-bucket limit on credential endpoints to slow down brute force / credential
 * stuffing. Buckets live in a bounded Caffeine cache so memory can't grow without limit.
 * (For several instances you'd move this to Redis, e.g. Bucket4j + Redis.)
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/api/auth/login", "/api/auth/register");

    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();
    private final int capacity;
    private final double refillPerSecond;

    public AuthRateLimitFilter(AppProperties props) {
        this.capacity = props.rateLimit().capacity();
        this.refillPerSecond = props.rateLimit().refillPerMinute() / 60d;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // getRemoteAddr() honours X-Forwarded-For because server.forward-headers-strategy=framework.
        var bucket = buckets.get(request.getRemoteAddr(), _ -> new TokenBucket(capacity, refillPerSecond));
        if (bucket.tryConsume()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(Math.max(1, bucket.secondsUntilNextToken())));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Too many requests","status":429,\
                "detail":"Too many attempts. Please wait a moment and try again."}""");
    }
}
