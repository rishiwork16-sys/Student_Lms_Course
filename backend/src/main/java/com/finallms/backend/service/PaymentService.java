package com.finallms.backend.service;

import com.finallms.backend.dto.PaymentDto;
import com.finallms.backend.entity.Course;
import com.finallms.backend.entity.Payment;
import com.finallms.backend.entity.User;
import com.finallms.backend.enums.PaymentStatus;
import com.finallms.backend.repository.CourseRepository;
import com.finallms.backend.repository.PaymentRepository;
import com.finallms.backend.repository.UserRepository;
import com.finallms.backend.repository.EnrollmentRepository;
import com.finallms.backend.entity.Enrollment;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import javax.annotation.PostConstruct;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class PaymentService {

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private RazorpayClient client;

    @PostConstruct
    public void init() {
        try {
            if (keyId != null && !keyId.contains("placeholder")) {
                this.client = new RazorpayClient(keyId, keySecret);
            }
            if (webhookSecret == null || webhookSecret.isBlank() || webhookSecret.contains("placeholder")) {
                String envSecret = System.getenv("RAZORPAY_WEBHOOK_SECRET");
                if (envSecret != null && !envSecret.isBlank()) {
                    webhookSecret = envSecret.trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Razorpay Init Failed: " + e.getMessage());
        }
    }

    public PaymentDto.OrderResponse createOrder(String userEmailOrPhone, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (course.getPrice() == null || course.getPrice() <= 0) {
            throw new RuntimeException("Course is free, no payment needed");
        }

        User user = userRepository.findByEmail(userEmailOrPhone)
                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", (int) (course.getPrice() * 100)); // Amount in paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());

            if (client == null)
                throw new RuntimeException("Razorpay credentials not configured");

            Order order = client.orders.create(orderRequest);

            PaymentDto.OrderResponse response = new PaymentDto.OrderResponse();
            response.setOrderId(order.get("id").toString());
            response.setAmount(course.getPrice());
            response.setCurrency("INR");
            response.setKeyId(keyId);

            Payment payment = new Payment();
            payment.setOrderId(response.getOrderId());
            payment.setAmount(response.getAmount());
            payment.setStatus(PaymentStatus.CREATED);
            payment.setUser(user);
            payment.setUserName(user.getName());
            payment.setCourse(course);
            payment.setCreatedAt(java.time.LocalDateTime.now());
            payment.setUpdatedAt(payment.getCreatedAt());
            paymentRepository.save(payment);

            return response;

        } catch (Exception e) {
            throw new RuntimeException("Error creating order: " + e.getMessage());
        }
    }

    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);

            return Utils.verifyPaymentSignature(options, keySecret);
        } catch (Exception e) {
            System.err.println("Verification failed: " + e.getMessage());
            return false;
        }
    }

    public boolean handleWebhook(String payload, String signature) {
        try {
            if (webhookSecret == null || webhookSecret.contains("placeholder")) {
                return false;
            }
            if (signature == null || signature.isBlank()) {
                return false;
            }
            boolean verified = Utils.verifyWebhookSignature(payload, signature, webhookSecret);
            if (!verified) {
                return false;
            }
            JSONObject json = new JSONObject(payload);
            String event = json.optString("event", "");
            String orderId = null;
            String paymentId = null;
            String errorReason = null;
            Integer amountPaise = null;
            if (json.has("payload")) {
                JSONObject p = json.getJSONObject("payload");
                if (p.has("payment")) {
                    JSONObject pe = p.getJSONObject("payment").getJSONObject("entity");
                    orderId = pe.optString("order_id", null);
                    paymentId = pe.optString("id", null);
                    errorReason = pe.optString("error_description", null);
                    if (errorReason == null) {
                        errorReason = pe.optString("error_reason", null);
                    }
                    if (pe.has("amount")) {
                        amountPaise = pe.getInt("amount");
                    }
                }
                if (orderId == null && p.has("order")) {
                    JSONObject oe = p.getJSONObject("order").getJSONObject("entity");
                    orderId = oe.optString("id", null);
                }
            }
            if (orderId == null) {
                return true;
            }
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            if (payment == null) {
                return true;
            }
            if (amountPaise != null && payment.getAmount() != null) {
                long expected = Math.round(payment.getAmount() * 100);
                if (expected != amountPaise.longValue()) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setErrorReason("Amount mismatch");
                    payment.setUpdatedAt(java.time.LocalDateTime.now());
                    paymentRepository.save(payment);
                    return true;
                }
            }
            if ((event.equalsIgnoreCase("payment.captured") || event.equalsIgnoreCase("order.paid"))
                    && payment.getStatus() == PaymentStatus.SUCCESS) {
                return true;
            }
            if (event.equalsIgnoreCase("payment.captured") || event.equalsIgnoreCase("order.paid")) {
                payment.setStatus(PaymentStatus.SUCCESS);
                if (paymentId != null) payment.setPaymentId(paymentId);
                payment.setSignature(signature);
                payment.setUpdatedAt(java.time.LocalDateTime.now());
                paymentRepository.save(payment);
                if (payment.getUser() != null && payment.getCourse() != null) {
                    boolean exists = enrollmentRepository.findByUserAndCourseId(payment.getUser(), payment.getCourse().getId()).isPresent();
                    if (!exists) {
                        Enrollment enrollment = new Enrollment();
                        enrollment.setUser(payment.getUser());
                        enrollment.setCourse(payment.getCourse());
                        enrollment.setEnrolledAt(java.time.LocalDateTime.now());
                        enrollmentRepository.save(enrollment);
                    }
                }
            } else if (event.equalsIgnoreCase("payment.failed") || event.equalsIgnoreCase("order.failed")) {
                payment.setStatus(PaymentStatus.FAILED);
                if (paymentId != null) payment.setPaymentId(paymentId);
                payment.setSignature(signature);
                if (errorReason != null) payment.setErrorReason(errorReason);
                payment.setUpdatedAt(java.time.LocalDateTime.now());
                paymentRepository.save(payment);
            } else if (event.equalsIgnoreCase("refund.processed")) {
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setUpdatedAt(java.time.LocalDateTime.now());
                paymentRepository.save(payment);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void reconcilePendingPayments() {
        try {
            if (client == null) {
                return;
            }
            java.util.List<Payment> pending = paymentRepository
                    .findTop50ByStatusOrderByCreatedAtDesc(PaymentStatus.CREATED);
            for (Payment p : pending) {
                try {
                    if (p.getOrderId() == null) continue;
                    Order order = client.orders.fetch(p.getOrderId());
                    String orderStatus = order != null ? order.get("status").toString() : null;
                    boolean paid = orderStatus != null && orderStatus.equalsIgnoreCase("paid");
                    if (!paid) {
                        java.util.List<com.razorpay.Payment> payments = client.orders.fetchPayments(p.getOrderId());
                        for (com.razorpay.Payment payObj : payments) {
                            boolean captured = false;
                            String status = "";
                            String paymentId = null;
                            try {
                                Object cap = payObj.get("captured");
                                if (cap != null) captured = Boolean.parseBoolean(cap.toString());
                            } catch (Exception ignore) {}
                            try {
                                Object st = payObj.get("status");
                                if (st != null) status = st.toString();
                            } catch (Exception ignore) {}
                            try {
                                Object pid = payObj.get("id");
                                if (pid != null) paymentId = pid.toString();
                            } catch (Exception ignore) {}
                            if (captured || "captured".equalsIgnoreCase(status)) {
                                p.setStatus(PaymentStatus.SUCCESS);
                                if (paymentId != null) p.setPaymentId(paymentId);
                                p.setUpdatedAt(java.time.LocalDateTime.now());
                                paymentRepository.save(p);
                                enrollIfNeeded(p);
                                paid = true;
                                break;
                            }
                        }
                    } else {
                        p.setStatus(PaymentStatus.SUCCESS);
                        p.setUpdatedAt(java.time.LocalDateTime.now());
                        paymentRepository.save(p);
                        enrollIfNeeded(p);
                    }
                } catch (Exception ex) {
                    // ignore individual failures, continue
                }
            }
        } catch (Exception e) {
            // swallow to avoid scheduler crash
        }
    }

    private void enrollIfNeeded(Payment payment) {
        try {
            if (payment.getUser() == null || payment.getCourse() == null) return;
            boolean exists = enrollmentRepository.findByUserAndCourseId(
                    payment.getUser(), payment.getCourse().getId()).isPresent();
            if (!exists) {
                Enrollment enrollment = new Enrollment();
                enrollment.setUser(payment.getUser());
                enrollment.setCourse(payment.getCourse());
                enrollment.setEnrolledAt(java.time.LocalDateTime.now());
                enrollmentRepository.save(enrollment);
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
