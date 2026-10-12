package com.rohan.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.rohan.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, String>{
}