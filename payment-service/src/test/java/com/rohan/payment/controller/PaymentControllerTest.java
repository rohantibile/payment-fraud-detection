package com.rohan.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rohan.payment.dto.PaymentRequest;
import com.rohan.payment.dto.PaymentResponse;
import com.rohan.payment.entity.PaymentStatus;
import com.rohan.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP-level tests without starting Spring Boot, Kafka or a database: a standalone MockMvc
 * around the real controller, with the service mocked.
 */
class PaymentControllerTest {

    private final PaymentService service = mock(PaymentService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service)).build();

    private static PaymentResponse response(String id) {
        return new PaymentResponse(id, "CARD-1", new BigDecimal("2500.50"), "Amazon", "Pune",
                PaymentStatus.PENDING, null, Instant.parse("2026-11-18T05:00:00Z"));
    }

    private static String body(String cardId, String amount, String merchant, String city) {
        return "{\"cardId\":\"" + cardId + "\",\"amount\":" + amount
                + ",\"merchant\":\"" + merchant + "\",\"city\":\"" + city + "\"}";
    }

    @Test
    void validPostReturns201WithPendingStatus() throws Exception {
        when(service.create(any(PaymentRequest.class))).thenReturn(response("abc"));

        String json = mvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CARD-1", "2500.50", "Amazon", "Pune")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(json).contains("\"id\":\"abc\"").contains("PENDING");
    }

    @Test
    void blankCityReturns400() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CARD-1", "100", "Amazon", "")))
                .andExpect(status().isBadRequest());

        verify(service, never()).create(any(PaymentRequest.class));
    }

    @Test
    void negativeAmountReturns400() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CARD-1", "-5", "Amazon", "Pune")))
                .andExpect(status().isBadRequest());

        verify(service, never()).create(any(PaymentRequest.class));
    }

    @Test
    void missingFieldReturns400() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\":\"CARD-1\",\"amount\":100,\"merchant\":\"Amazon\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturns200() throws Exception {
        when(service.get("abc")).thenReturn(response("abc"));

        String json = mvc.perform(get("/api/v1/payments/abc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json).contains("\"id\":\"abc\"");
    }

    @Test
    void getUnknownIdReturns404() throws Exception {
        when(service.get("nope")).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: nope"));

        mvc.perform(get("/api/v1/payments/nope")).andExpect(status().isNotFound());
    }
}
