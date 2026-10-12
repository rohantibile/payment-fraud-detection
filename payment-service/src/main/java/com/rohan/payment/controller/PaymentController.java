package com.rohan.payment.controller;

import com.rohan.payment.dto.*;
import com.rohan.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController (PaymentService service) {
        this.service = service;
    }
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse  create(@Valid @RequestBody PaymentRequest request) {
        return service.create(request);
    }

    @GetMapping
    public PaymentResponse get(@PathVariable String id) {
        return service.get(id);
    }
}