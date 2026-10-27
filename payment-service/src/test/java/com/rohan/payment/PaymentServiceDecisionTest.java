package com.rohan.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.rohan.payment.dto.FraudDecisionEvent;
import com.rohan.payment.entity.Payment;
import com.rohan.payment.entity.PaymentStatus;
import com.rohan.payment.kafka.PaymentEventProducer;
import com.rohan.payment.repository.PaymentRepository;
import com.rohan.payment.service.PaymentService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentServiceDecisionTest {

    private final PaymentRepository repo = mock(PaymentRepository.class);
    private final PaymentService service = new PaymentService(repo, mock(PaymentEventProducer.class));

    private FraudDecisionEvent decision(Payment p, String d) {
        return new FraudDecisionEvent(p.getId(), p.getCardId(), d, "VELOCITY: test", Instant.now());
    }

    @Test
    void pendingPaymentBecomesBlocked() {
        Payment p = new Payment();
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));
        service.applyDecision(decision(p, "BLOCKED"));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.BLOCKED);
        assertThat(p.getStatusReason()).contains("VELOCITY");
    }

    @Test
    void finalStatusIsNotOverwritten() {
        Payment p = new Payment();
        p.setStatus(PaymentStatus.APPROVED);
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));
        service.applyDecision(decision(p, "BLOCKED"));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }
}