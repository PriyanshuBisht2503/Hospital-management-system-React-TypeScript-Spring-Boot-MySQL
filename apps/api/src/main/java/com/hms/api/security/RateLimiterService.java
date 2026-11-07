package com.hms.api.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hms.api.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * In-memory rate limiting backed by Caffeine expiring cache.
 * Avoids memory leaks by automatically evicting buckets after inactivity.
 */
@Service
public class RateLimiterService {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(15))
            .maximumSize(50_000)
            .build();

    /**
     * Rate limit for login by client IP (20 requests per minute).
     */
    public void checkLoginIpLimit(String clientIp) {
        Bucket bucket = buckets.get("login:ip:" + clientIp, k -> createBucket(20, Duration.ofMinutes(1)));
        if (bucket == null || !bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many login attempts from this IP address. Please try again later.");
        }
    }

    /**
     * Rate limit for login by target email (10 requests per minute per target account).
     */
    public void checkLoginEmailLimit(String email) {
        if (email == null || email.isBlank()) return;
        Bucket bucket = buckets.get("login:email:" + email.trim().toLowerCase(), k -> createBucket(10, Duration.ofMinutes(1)));
        if (bucket == null || !bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many login attempts for this account. Please try again later.");
        }
    }

    /**
     * Rate limit for OTP requests by client IP (5 requests per minute).
     */
    public void checkOtpLimit(String clientIp) {
        Bucket bucket = buckets.get("otp:ip:" + clientIp, k -> createBucket(5, Duration.ofMinutes(1)));
        if (bucket == null || !bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many OTP requests from this IP address. Please try again later.");
        }
    }

    /**
     * Rate limit for OTP requests per target email/phone (3 sends per 10 minutes).
     */
    public void checkOtpTargetLimit(String target) {
        if (target == null || target.isBlank()) return;
        Bucket bucket = buckets.get("otp:target:" + target.trim().toLowerCase(), k -> createBucket(3, Duration.ofMinutes(10)));
        if (bucket == null || !bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many OTP requests for this email or phone number. Please wait before requesting another code.");
        }
    }

    /**
     * Rate limit for password reset requests by client IP (5 requests per minute).
     */
    public void checkPasswordResetLimit(String clientIp) {
        Bucket bucket = buckets.get("pwd-reset:ip:" + clientIp, k -> createBucket(5, Duration.ofMinutes(1)));
        if (bucket == null || !bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many password reset requests from this IP address. Please try again later.");
        }
    }

    /**
     * Rate limit for booking requests per caller (20 per minute).
     */
    public void checkBookingLimit(String callerKey) {
        Bucket bucket = buckets.get("booking:" + callerKey, k -> createBucket(20, Duration.ofMinutes(1)));
        if (bucket == null || !bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many booking attempts. Please try again later.");
        }
    }

    public void clear() {
        buckets.invalidateAll();
    }

    private Bucket createBucket(long capacity, Duration refillPeriod) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillPeriod)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
