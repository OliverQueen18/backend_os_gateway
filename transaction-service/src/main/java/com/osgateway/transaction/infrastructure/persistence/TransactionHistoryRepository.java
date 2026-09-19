package com.osgateway.transaction.infrastructure.persistence;

import com.osgateway.transaction.domain.TransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, Long> {
    List<TransactionHistory> findByTransactionIdOrderByCreatedAtAsc(Long transactionId);
}