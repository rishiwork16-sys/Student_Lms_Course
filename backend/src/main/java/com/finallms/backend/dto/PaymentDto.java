package com.finallms.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class PaymentDto {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderRequest {
        private Long courseId;

        public Long getCourseId() {
            return courseId;
        }

        public void setCourseId(Long courseId) {
            this.courseId = courseId;
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderResponse {
        private String orderId;
        private Double amount;
        private String currency;
        private String keyId; // To pass to frontend

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public Double getAmount() {
            return amount;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VerifyRequest {
        private String razorpayOrderId;
        private String razorpayPaymentId;
        private String razorpaySignature;
        private Long courseId;

        public String getRazorpayOrderId() {
            return razorpayOrderId;
        }

        public void setRazorpayOrderId(String razorpayOrderId) {
            this.razorpayOrderId = razorpayOrderId;
        }

        public String getRazorpayPaymentId() {
            return razorpayPaymentId;
        }

        public void setRazorpayPaymentId(String razorpayPaymentId) {
            this.razorpayPaymentId = razorpayPaymentId;
        }

        public String getRazorpaySignature() {
            return razorpaySignature;
        }

        public void setRazorpaySignature(String razorpaySignature) {
            this.razorpaySignature = razorpaySignature;
        }

        public Long getCourseId() {
            return courseId;
        }

        public void setCourseId(Long courseId) {
            this.courseId = courseId;
        }
    }

    public static class AdminPaymentResponse {
        private Long id;
        private String orderId;
        private String paymentId;
        private String signature;
        private Double amount;
        private String status;
        private String userName;
        private Long userId;
        private Long courseId;
        private String errorReason;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;

        public AdminPaymentResponse() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public String getErrorReason() { return errorReason; }
        public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
