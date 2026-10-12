package com.tpm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private final AtomicLong now = new AtomicLong();
    private final TokenBucket bucket = new TokenBucket(3, 1.0, now::get); // 3 burst, 1 token/second

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
        assertThat(bucket.secondsUntilNextToken()).isEqualTo(1);
    }

    @Test
    void refillsOverTimeButNeverAboveCapacity() {
        for (int i = 0; i < 3; i++) bucket.tryConsume();
        now.addAndGet(TimeUnit.MILLISECONDS.toNanos(1_500));
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();

        now.addAndGet(TimeUnit.HOURS.toNanos(1));
        int allowed = 0;
        while (bucket.tryConsume()) allowed++;
        assertThat(allowed).isEqualTo(3);
    }
}
