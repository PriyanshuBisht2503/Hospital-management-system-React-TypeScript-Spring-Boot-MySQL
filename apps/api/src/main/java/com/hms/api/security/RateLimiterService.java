package com.hms.api.security;

import com.hms.api.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Rate limit for login by client IP (20 requests per minute).
     */
    public void checkLoginIpLimit(String clientIp) {
        Bucket bucket = buckets.computeIfAbsent("login:ip:" + clientIp, k -> createBucket(20, Duration.ofMinutes(1)));
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many login attempts from this IP address. Please try again later.");
        }
    }

    /**
     * Rate limit for login by target email (10 requests per minute per target account).
     */
    public void checkLoginEmailLimit(String email) {
        if (email == null || email.isBlank()) return;
        Bucket bucket = buckets.computeIfAbsent("login:email:" + email.trim().toLowerCase(), k -> createBucket(10, Duration.ofMinutes(1)));
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many login attempts for this account. Please try again later.");
        }
    }

    /**
     * Rate limit for OTP requests by client IP (5 requests per minute).
     */
    public void checkOtpLimit(String clientIp) {
        Bucket bucket = buckets.computeIfAbsent("otp:ip:" + clientIp, k -> createBucket(5, Duration.ofMinutes(1)));
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many OTP requests from this IP address. Please try again later.");
        }
    }

    /**
     * Rate limit for password reset requests by client IP (5 requests per minute).
     */
    public void checkPasswordResetLimit(String clientIp) {
        Bucket bucket = buckets.computeIfAbsent("pwd-reset:ip:" + clientIp, k -> createBucket(5, Duration.ofMinutes(1)));
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many password reset requests from this IP address. Please try again later.");
        }
    }

    public void clear() {
        buckets.clear();
    }

    private Bucket createBucket(long capacity, Duration refillPeriod) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillPeriod)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
