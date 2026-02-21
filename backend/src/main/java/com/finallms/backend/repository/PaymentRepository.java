package com.finallms.backend.repository;

import com.finallms.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findTop50ByOrderByCreatedAtDesc();

    List<Payment> findTop50ByStatusOrderByCreatedAtDesc(com.finallms.backend.enums.PaymentStatus status);

    void deleteByCourse(com.finallms.backend.entity.Course course);
}
