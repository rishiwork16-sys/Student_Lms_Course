package com.finallms.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentWebhookController {

    @Autowired
    private com.finallms.backend.service.PaymentService paymentService;

    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody String payload,
                                     @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {
        boolean ok = paymentService.handleWebhook(payload, signature);
        if (ok) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(400).body("Invalid webhook");
        }
    }
}
