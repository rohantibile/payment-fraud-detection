package com.rohan.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rohan.payment.dto.FraudDecisionEvent;
import com.rohan.payment.dto.PaymentEvent;
import com.rohan.payment.dto.PaymentRequest;
import com.rohan.payment.dto.PaymentResponse;
import com.rohan.payment.entity.Payment;
import com.rohan.payment.entity.PaymentStatus;
import com.rohan.payment.kafka.PaymentEventProducer;
import com.rohan.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.web.server.ResponseStatusException;

/** create(), get() and the edge cases of applyDecision(). Repository and producer are mocked. */
class PaymentServiceTest {

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final PaymentEventProducer producer = mock(PaymentEventProducer.class);
    private final PaymentService service = new PaymentService(repository, producer);

    @BeforeEach
    void setUp() {
        when(repository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createStoresAPendingPaymentAndPublishesAnEvent() {
        PaymentResponse response = service.create(
                new PaymentRequest("CARD-1", new BigDecimal("2500.50"), "Amazon", "Pune"));

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.id()).isNotBlank();

        ArgumentCaptor<PaymentEvent> event = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(producer).publish(event.capture());
        assertThat(event.getValue().paymentId()).isEqualTo(response.id());
        assertThat(event.getValue().cardId()).isEqualTo("CARD-1");
        assertThat(event.getValue().city()).isEqualTo("Pune");
        assertThat(event.getValue().amount()).isEqualByComparingTo("2500.50");
    }

    @Test
    void createSavesBeforePublishing() {
        service.create(new PaymentRequest("CARD-1", new BigDecimal("10"), "Shop", "Pune"));

        InOrder order = inOrder(repository, producer);
        order.verify(repository).save(any(Payment.class));
        order.verify(producer).publish(any(PaymentEvent.class));
    }

    @Test
    void getReturnsAnExistingPayment() {
        Payment p = new Payment();
        p.setCardId("CARD-1");
        p.setAmount(new BigDecimal("5"));
        p.setMerchant("Shop");
        p.setCity("Pune");
        when(repository.findById(p.getId())).thenReturn(Optional.of(p));

        assertThat(service.get(p.getId()).id()).isEqualTo(p.getId());
    }

    @Test
    void getThrowsNotFoundForAnUnknownId() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("nope"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void decisionForAnUnknownPaymentIsIgnored() {
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        assertThatCode(() -> service.applyDecision(
                new FraudDecisionEvent("ghost", "CARD-1", "BLOCKED", "x", Instant.now())))
                .doesNotThrowAnyException();
        verify(repository, never()).save(any(Payment.class));
    }

    @Test
    void longReasonIsTruncatedTo250Characters() {
        Payment p = new Payment();
        when(repository.findById(p.getId())).thenReturn(Optional.of(p));

        service.applyDecision(new FraudDecisionEvent(p.getId(), "CARD-1", "FLAGGED",
                "x".repeat(300), Instant.now()));

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FLAGGED);
        assertThat(p.getStatusReason()).hasSize(250);
    }
}
