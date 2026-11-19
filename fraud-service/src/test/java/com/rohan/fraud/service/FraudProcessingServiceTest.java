package com.rohan.fraud.service;

import static com.rohan.fraud.TestData.NOW;
import static com.rohan.fraud.TestData.event;
import static com.rohan.fraud.TestData.past;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rohan.fraud.dto.FraudDecisionEvent;
import com.rohan.fraud.dto.PaymentEvent;
import com.rohan.fraud.engine.FraudEngine;
import com.rohan.fraud.kafka.FraudDecisionProducer;
import com.rohan.fraud.model.Decision;
import com.rohan.fraud.model.TransactionRecord;
import com.rohan.fraud.repository.TransactionRepository;
import com.rohan.fraud.rules.AmountRule;
import com.rohan.fraud.rules.GeoMismatchRule;
import com.rohan.fraud.rules.VelocityRule;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests the orchestration in FraudProcessingService. The repository and the Kafka
 * producer are mocked; the rule engine is the real one with the three real rules.
 */
class FraudProcessingServiceTest {

    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final FraudDecisionProducer producer = mock(FraudDecisionProducer.class);
    private final FraudEngine engine =
            new FraudEngine(List.of(new VelocityRule(), new AmountRule(), new GeoMismatchRule()));

    private FraudProcessingService service;

    @BeforeEach
    void setUp() {
        service = new FraudProcessingService(repository, engine, producer);
        when(repository.findByCardIdAndCreatedAtGreaterThanEqual(anyString(), any(Instant.class)))
                .thenReturn(List.of());
    }

    @Test
    void duplicatePaymentIsSkippedCompletely() {
        PaymentEvent e = event("Pune", "100");
        when(repository.existsById(e.paymentId())).thenReturn(true);

        service.process(e);

        verify(producer, never()).publish(any(FraudDecisionEvent.class));
        verify(repository, never()).save(any(TransactionRecord.class));
        verify(repository, never())
                .findByCardIdAndCreatedAtGreaterThanEqual(anyString(), any(Instant.class));
    }

    @Test
    void normalPaymentIsApprovedPublishedAndSaved() {
        PaymentEvent e = event("Pune", "2500");

        service.process(e);

        ArgumentCaptor<FraudDecisionEvent> published = ArgumentCaptor.forClass(FraudDecisionEvent.class);
        verify(producer).publish(published.capture());
        assertThat(published.getValue().decision()).isEqualTo(Decision.APPROVED);
        assertThat(published.getValue().paymentId()).isEqualTo(e.paymentId());
        assertThat(published.getValue().cardId()).isEqualTo("CARD-1");

        ArgumentCaptor<TransactionRecord> saved = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPaymentId()).isEqualTo(e.paymentId());
        assertThat(saved.getValue().getDecision()).isEqualTo(Decision.APPROVED);
    }

    @Test
    void fourthRapidPaymentIsBlockedByVelocity() {
        PaymentEvent e = event("Pune", "100");
        when(repository.findByCardIdAndCreatedAtGreaterThanEqual(eq("CARD-1"), any(Instant.class)))
                .thenReturn(List.of(past("Pune", 10), past("Pune", 20), past("Pune", 30)));

        service.process(e);

        ArgumentCaptor<FraudDecisionEvent> published = ArgumentCaptor.forClass(FraudDecisionEvent.class);
        verify(producer).publish(published.capture());
        assertThat(published.getValue().decision()).isEqualTo(Decision.BLOCKED);
        assertThat(published.getValue().reason()).contains("VELOCITY");
    }

    @Test
    void historyIsReadForTheLastTenMinutes() {
        PaymentEvent e = event("Pune", "100");

        service.process(e);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findByCardIdAndCreatedAtGreaterThanEqual(eq("CARD-1"), since.capture());
        assertThat(since.getValue()).isEqualTo(NOW.minus(Duration.ofMinutes(10)));
    }

    @Test
    void verdictIsPublishedBeforeItIsSaved() {
        service.process(event("Pune", "100"));

        InOrder order = inOrder(producer, repository);
        order.verify(producer).publish(any(FraudDecisionEvent.class));
        order.verify(repository).save(any(TransactionRecord.class));
    }

    @Test
    void nothingIsSavedWhenPublishingFails() {
        doThrow(new IllegalStateException("kafka down"))
                .when(producer).publish(any(FraudDecisionEvent.class));

        assertThatThrownBy(() -> service.process(event("Pune", "100")))
                .isInstanceOf(IllegalStateException.class);

        verify(repository, never()).save(any(TransactionRecord.class));
    }

    @Test
    void simulatedFailureThrowsWhenSwitchedOn() {
        ReflectionTestUtils.setField(service, "simulateFailure", true);
        PaymentEvent e = new PaymentEvent("p-1", "CARD-1", new BigDecimal("100"),
                "SIMULATE_FAILURE", "Pune", NOW);

        assertThatThrownBy(() -> service.process(e))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated failure");

        verify(producer, never()).publish(any(FraudDecisionEvent.class));
        verify(repository, never()).save(any(TransactionRecord.class));
    }

    @Test
    void simulatedFailureMerchantIsIgnoredWhenSwitchedOff() {
        PaymentEvent e = new PaymentEvent("p-2", "CARD-1", new BigDecimal("100"),
                "SIMULATE_FAILURE", "Pune", NOW);

        service.process(e);

        verify(producer).publish(any(FraudDecisionEvent.class));
    }
}
