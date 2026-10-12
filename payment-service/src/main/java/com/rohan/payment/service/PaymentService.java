package com.rohan.payment.service;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.rohan.payment.dto.*;
import com.rohan.payment.entity.Payment;
import com.rohan.payment.repository.PaymentRepository;

@Service 
public class PaymentService {
       private final PaymentRepository repository;

       public PaymentService (PaymentRepository repository) {
            this.repository = repository;
       }

       public PaymentResponse create(PaymentRequest request ) {
            Payment payment = new Payment();
            payment.setCardId(request.cardId());
            payment.setAmount(request.amount());
            payment.setMerchant(request.merchant());
            payment.setCity(request.city());
            return PaymentResponse.from(repository.save(payment));
       }

       public PaymentResponse get(String id) {
            return repository.findById(id)
                    .map(PaymentResponse::from)
                    .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found" + id
                    ));
       }
}