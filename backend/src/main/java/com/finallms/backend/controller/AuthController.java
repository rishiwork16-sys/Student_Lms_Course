package com.finallms.backend.controller;

import com.finallms.backend.dto.AuthDto;
import com.finallms.backend.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/admin/login")
    public ResponseEntity<AuthDto.AuthResponse> adminLogin(@RequestBody AuthDto.LoginRequest request) {
        return ResponseEntity.ok(authService.loginAdmin(request));
    }

    @PostMapping("/student/otp")
    public ResponseEntity<String> sendOtp(@RequestBody AuthDto.OtpRequest request) {
        try {
          String ans =   authService.sendOtp(request.getPhone());
           if(ans.length()==6){
             return ResponseEntity.ok("OTP sent successfully.");
           }
           else{
            return ResponseEntity.status(429).body(ans);
           }
        } catch (RuntimeException e) {
            return ResponseEntity.status(429).body(e.getMessage());
        }
    }

    @PostMapping("/student/verify-login")
    public ResponseEntity<AuthDto.AuthResponse> verifyLoginOtp(@RequestBody AuthDto.VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyLoginOtp(request));
    }

    @PostMapping("/student/verify-register")
    public ResponseEntity<AuthDto.AuthResponse> verifyRegistrationOtp(@RequestBody AuthDto.VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyRegistrationOtp(request));
    }

    @PostMapping("/student/verify")
    public ResponseEntity<AuthDto.AuthResponse> verifySmart(@RequestBody AuthDto.VerifyOtpRequest request) {
        String normalizedPhone = request.getPhone() != null
                ? request.getPhone().replaceAll("[^0-9]", "").trim()
                : "";
        request.setPhone(normalizedPhone);
        boolean exists = authService.userExistsByPhone(normalizedPhone);
        if (!exists) {
            throw new com.finallms.backend.exception.BadRequestException(
                    "New registrations are currently closed. Please contact support.");
        }
        return ResponseEntity.ok(authService.verifyLoginOtp(request));
    }

    @GetMapping("/student/check")
    public ResponseEntity<?> checkStudentExists(@RequestParam String phone) {
        return ResponseEntity.ok(authService.getStudentCheck(phone));
    }

    @PostMapping("/student/email/otp")
    public ResponseEntity<String> sendEmailOtp(@RequestBody AuthDto.EmailOtpRequest request) {
        try {
            String incoming = request != null ? request.getEmail() : null;
            System.out.println("EMAIL OTP incoming: [" + incoming + "]");
            if (incoming == null || incoming.trim().isEmpty() || !incoming.contains("@")) {
                return ResponseEntity.badRequest().body("Invalid email");
            }
            String normalized = incoming.trim().toLowerCase();
            String ans = authService.sendEmailOtp(normalized);
            if (ans.length() == 6) {
                return ResponseEntity.ok("OTP sent successfully.");
            } else {
                return ResponseEntity.status(429).body(ans);
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(429).body(e.getMessage());
        }
    }

    @PostMapping("/student/login-password")
    public ResponseEntity<AuthDto.AuthResponse> studentPasswordLogin(@RequestBody AuthDto.LoginRequest request) {
        return ResponseEntity.ok(authService.loginStudentWithPassword(request));
    }

    @PostMapping("/student/email/verify")
    public ResponseEntity<AuthDto.AuthResponse> verifyEmailSmart(@RequestBody AuthDto.VerifyEmailOtpRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        boolean exists = authService.userExistsByEmail(email);
        if (!exists) {
            throw new com.finallms.backend.exception.BadRequestException(
                    "New registrations are currently closed. Please contact support.");
        }
        return ResponseEntity.ok(authService.verifyLoginEmailOtp(request));
    }

    @GetMapping("/student/email/check")
    public ResponseEntity<?> checkStudentExistsByEmail(@RequestParam String email) {
        String normalized = email != null ? email.trim().toLowerCase() : "";
        boolean exists = authService.userExistsByEmail(normalized);
        return ResponseEntity.ok(java.util.Map.of("exists", exists));
    }

    @PostMapping("/student/guest")
    public ResponseEntity<AuthDto.AuthResponse> guestLogin() {
        return ResponseEntity.ok(authService.createGuestUser());
    }
}
