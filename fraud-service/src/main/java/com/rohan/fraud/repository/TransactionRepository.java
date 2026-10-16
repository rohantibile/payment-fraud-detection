package com.rohan.fraud.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.rohan.fraud.model.TransactionRecord;

public interface TransactionRepository extends JpaRepository<TransactionRecord, String> {
    List<TransactionRecord> findByCardIdAndCreatedAtGreaterThanEqual(String cardId, Instant since);
}
