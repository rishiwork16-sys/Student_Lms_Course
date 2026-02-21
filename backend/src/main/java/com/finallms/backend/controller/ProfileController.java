package com.finallms.backend.controller;

import com.finallms.backend.dto.ProfileDto;
import com.finallms.backend.service.StudentService;
import com.finallms.backend.service.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/student/profile")
public class ProfileController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private OtpService otpService;

    @GetMapping
    public ResponseEntity<ProfileDto.ProfileResponse> getProfile(Authentication auth) {
        return ResponseEntity.ok(studentService.getProfile(auth.getName()));
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(Authentication auth, @RequestBody ProfileDto.ProfileUpdateRequest request) {
        studentService.updateProfile(auth.getName(), request);
        return ResponseEntity.ok("Profile updated successfully");
    }

    @PostMapping("/picture")
    public ResponseEntity<?> updateProfilePicture(Authentication auth, @RequestParam("file") MultipartFile file) {
        studentService.updateProfilePicture(auth.getName(), file);
        return ResponseEntity.ok("Profile picture updated successfully");
    }

    @PostMapping("/phone/request")
    public ResponseEntity<?> requestPhoneChange(Authentication auth,
            @RequestBody ProfileDto.PhoneChangeRequest request) {
        studentService.requestPhoneChange(auth.getName(), request.getNewPhone(), otpService);
        return ResponseEntity.ok("OTP sent to new phone number");
    }

    @PostMapping("/phone/verify")
    public ResponseEntity<?> verifyPhoneChange(Authentication auth,
            @RequestBody ProfileDto.PhoneVerifyRequest request) {
        studentService.verifyPhoneChange(auth.getName(), request.getNewPhone(), request.getOtp(), otpService);
        return ResponseEntity.ok("Phone number updated successfully");
    }
}
