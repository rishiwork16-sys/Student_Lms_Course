package com.finallms.backend.dto;

import lombok.Data;

public class ProfileDto {

    @Data
    public static class ProfileResponse {
        private Long id;
        private String name;
        private String email;
        private String phone;
        private String address;
        private String profilePictureUrl;
        private String role;
    }

    @Data
    public static class ProfileUpdateRequest {
        private String name;
        private String email;
        private String address;
    }

    @Data
    public static class PhoneChangeRequest {
        private String newPhone;
    }

    @Data
    public static class PhoneVerifyRequest {
        private String newPhone;
        private String otp;
    }
}
