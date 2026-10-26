package com.rohan.payment.service;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.rohan.payment.dto.*;
import com.rohan.payment.entity.Payment;
import com.rohan.payment.entity.PaymentStatus;
import com.rohan.payment.kafka.PaymentEventProducer;
import com.rohan.payment.repository.PaymentRepository;

@Service
public class PaymentService {
  private final PaymentRepository repository;
  private final PaymentEventProducer producer;
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentService.class);

  public PaymentService(PaymentRepository repository, PaymentEventProducer producer) {
    this.repository = repository;
    this.producer = producer;
  }

  public PaymentResponse create(PaymentRequest request) {
    Payment payment = new Payment();
    payment.setCardId(request.cardId());
    payment.setAmount(request.amount());
    payment.setMerchant(request.merchant());
    payment.setCity(request.city());
    Payment saved = repository.save(payment);
    producer.publish(PaymentEvent.from(saved));
    return PaymentResponse.from(saved);
  }

  public PaymentResponse get(String id) {
    return repository.findById(id)
        .map(PaymentResponse::from)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Payment not found: " + id));
  }

  @org.springframework.transaction.annotation.Transactional
  public void applyDecision(FraudDecisionEvent event) {
    Payment payment = repository.findById(event.paymentId()).orElse(null);
    if (payment == null) {
      log.warn("Decision for unknown payment {}", event.paymentId());
      return;
    }
    if (payment.getStatus() != PaymentStatus.PENDING) {
      log.info("Payment {} already {}, ignoring duplicate decision",
          payment.getId(), payment.getStatus());
      return;
    }
    String reason = event.reason();
    if (reason != null && reason.length() > 250) {
      reason = reason.substring(0, 250);
    }
    payment.setStatus(PaymentStatus.valueOf(event.decision()));
    payment.setStatusReason(reason);
    log.info("Payment {} is now {}", payment.getId(), payment.getStatus());
  }
}