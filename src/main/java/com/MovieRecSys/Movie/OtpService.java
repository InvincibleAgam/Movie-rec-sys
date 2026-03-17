package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_LENGTH = 6;
    private static final long OTP_VALIDITY_SECONDS = 300; // 5 minutes

    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    record OtpEntry(String code, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public String sendOtp(String email) {
        String code = generateOtp();
        otpStore.put(email.toLowerCase().trim(), new OtpEntry(code, Instant.now().plusSeconds(OTP_VALIDITY_SECONDS)));
        log.info("OTP generated for {}: {}", email, code);
        return code;
    }

    public boolean verifyOtp(String email, String code) {
        String key = email.toLowerCase().trim();
        OtpEntry entry = otpStore.get(key);
        if (entry == null || entry.isExpired()) {
            otpStore.remove(key);
            return false;
        }
        if (entry.code().equals(code.trim())) {
            otpStore.remove(key);
            return true;
        }
        return false;
    }

    private String generateOtp() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
