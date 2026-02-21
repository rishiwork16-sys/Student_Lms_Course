package com.finallms.backend.service;

import com.finallms.backend.exception.BadRequestException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OtpService {

    @Value("${fast2sms.api.key}")
    private String apiKey;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@skilledup.local}")
    private String mailFrom;

    @Value("${app.mail.test-mode:false}")
    private boolean mailTestMode;

    private static final String DLT_TEMPLATE_ID = "182903";
    private static final String FAST2SMS_URL = "https://www.fast2sms.com/dev/bulkV2";
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_SENDS_PER_HOUR = 5;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int BLOCK_MINUTES_ON_ABUSE = 15;

    // Cache: Key = Phone, Value = OTP
    // Expires 15 minutes after write (as requested)
    private Cache<String, String> otpCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    // Per-phone cooldown to prevent rapid resend
    private Cache<String, Boolean> resendCooldown = Caffeine.newBuilder()
            .expireAfterWrite(RESEND_COOLDOWN_SECONDS, TimeUnit.SECONDS)
            .maximumSize(5000)
            .build();

    // Per-email cooldown
    private Cache<String, Boolean> resendEmailCooldown = Caffeine.newBuilder()
            .expireAfterWrite(RESEND_COOLDOWN_SECONDS, TimeUnit.SECONDS)
            .maximumSize(5000)
            .build();

    // Per-hour send counter
    private Cache<String, AtomicInteger> sendCounterHour = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    // Failed validation attempts
    private Cache<String, AtomicInteger> failedAttempts = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    // Blocked phones due to abuse
    private Cache<String, Boolean> blockedPhones = Caffeine.newBuilder()
            .expireAfterWrite(BLOCK_MINUTES_ON_ABUSE, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.contains("placeholder")) {
            System.out.println("Fast2SMS API Key not configured. Using Mock Mode.");
        }
    }

    public String generateAndSendOtp(String phone) {
        String key = normalizeKey(phone);
        if (blockedPhones.getIfPresent(key) != null) {
            throw new BadRequestException("Too many OTP attempts. Try again later.");
        }
        if (resendCooldown.getIfPresent(key) != null) {
            throw new BadRequestException("Please wait before requesting OTP again.");
        }
        AtomicInteger counter = sendCounterHour.get(key, k -> new AtomicInteger(0));
        if (counter.get() >= MAX_SENDS_PER_HOUR) {
            blockedPhones.put(key, Boolean.TRUE);
            throw new BadRequestException("OTP request limit reached. Try after some time.");
        }
        counter.incrementAndGet();
        resendCooldown.put(key, Boolean.TRUE);
        String otp = String.format("%06d", new Random().nextInt(999999));

        otpCache.put(key, otp);

        System.out.println("OTP generated for " + key + " at " + java.time.LocalDateTime.now());

        boolean ans = sendSms(phone.replaceAll("[^0-9]", "").trim(), otp);
        return ans ? otp : "something wene Wrong";
    }

    public String generateAndSendEmailOtp(String email) {
        String key = normalizeKey(email);
        try {
            boolean cooldown = resendEmailCooldown.getIfPresent(key) != null;
            boolean blocked = blockedPhones.getIfPresent(key) != null;
            AtomicInteger cnt = sendCounterHour.get(key, k -> new AtomicInteger(0));
            System.out.println("EMAIL OTP key=" + key + " cooldown=" + cooldown + " blocked=" + blocked + " count=" + (cnt != null ? cnt.get() : 0));
        } catch (Exception ex) {
            System.out.println("EMAIL OTP debug error: " + ex.getMessage());
        }
        if (blockedPhones.getIfPresent(key) != null) {
            throw new BadRequestException("Too many OTP attempts. Try again later.");
        }
        if (resendEmailCooldown.getIfPresent(key) != null) {
            throw new BadRequestException("Please wait before requesting OTP again.");
        }
        AtomicInteger counter = sendCounterHour.get(key, k -> new AtomicInteger(0));
        if (counter.get() >= MAX_SENDS_PER_HOUR) {
            blockedPhones.put(key, Boolean.TRUE);
            throw new BadRequestException("OTP request limit reached. Try after some time.");
        }
        counter.incrementAndGet();
        resendEmailCooldown.put(key, Boolean.TRUE);
        String otp = String.format("%06d", new Random().nextInt(999999));
        otpCache.put(key, otp);
        if (sendEmail(email, otp)) {
            return otp;
        }
        return "something wene Wrong";
    }

    public boolean validateOtp(String identifier, String otp) {
        String key = normalizeKey(identifier);
        String normalizedOtp = otp.replaceAll("[^0-9]", "").trim();
        String cachedOtp = otpCache.getIfPresent(key);

        if (blockedPhones.getIfPresent(key) != null) {
            return false;
        }

        if (cachedOtp != null && cachedOtp.trim().equals(normalizedOtp)) {
            otpCache.invalidate(key);
            failedAttempts.invalidate(key);
            return true;
        }
        AtomicInteger fails = failedAttempts.get(key, k -> new AtomicInteger(0));
        if (fails.incrementAndGet() >= MAX_FAILED_ATTEMPTS) {
            blockedPhones.put(key, Boolean.TRUE);
            otpCache.invalidate(key);
        }
        return false;
    }

    private boolean sendSms(String toPhone, String otp) {
        if (apiKey == null || apiKey.contains("placeholder")) {
            System.out.println("SIMULATED SMS to " + toPhone);
            return false;
        }

        try {
            String message = "Your LMS Login OTP is " + otp;
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);

            String url = FAST2SMS_URL
                    + "?authorization=" + apiKey
                    + "&route=dlt"
                    + "&sender_id=SKLDUP"
                    + "&message=" + DLT_TEMPLATE_ID
                    + "&variables_values=" + otp
                    + "&numbers=" + toPhone
                    + "&flash=0";

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            System.out.println("Fast2SMS Response: " + response.body());
            return true;
        } catch (Exception e) {
            System.err.println("Failed to send OTP via Fast2SMS: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    private boolean sendEmail(String to, String otp) {
        try {
            if (mailTestMode || mailSender == null) {
                System.out.println("DEV EMAIL OTP to " + to + ": " + otp);
                return true;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setFrom(mailFrom);
            message.setSubject("Your Login OTP");
            message.setText("Your LMS login OTP is " + otp + ". It will expire in 15 minutes.");
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to send OTP via Email: " + e.getMessage());
        }
        return false;
    }

    private String normalizeKey(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.contains("@")) {
            return trimmed.toLowerCase();
        }
        return trimmed.replaceAll("[^0-9]", "");
    }
}
