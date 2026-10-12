package com.tpm.common;

import java.util.function.LongSupplier;

/**
 * Classic token-bucket rate limiter: holds up to {@code capacity} tokens and refills
 * continuously at {@code refillPerSecond}. Each request consumes one token; when the bucket
 * is empty the request is rejected. Allows short bursts while enforcing an average rate.
 */
public final class TokenBucket {

    private final long capacity;
    private final double refillPerNano;
    private final LongSupplier nanoClock;
    private double tokens;
    private long lastRefill;

    public TokenBucket(long capacity, double refillPerSecond) {
        this(capacity, refillPerSecond, System::nanoTime);
    }

    TokenBucket(long capacity, double refillPerSecond, LongSupplier nanoClock) {
        if (capacity <= 0 || refillPerSecond <= 0) throw new IllegalArgumentException("capacity and rate must be > 0");
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000d;
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.lastRefill = nanoClock.getAsLong();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    /** Seconds until the next token is available (for the Retry-After header). */
    public synchronized long secondsUntilNextToken() {
        refill();
        if (tokens >= 1) return 0;
        return (long) Math.ceil((1 - tokens) / refillPerNano / 1_000_000_000d);
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
        lastRefill = now;
    }
}
