package com.rohan.fraud.engine;

import com.rohan.fraud.model.Decision;

public record FraudVerdict(Decision decision, String reason) { }