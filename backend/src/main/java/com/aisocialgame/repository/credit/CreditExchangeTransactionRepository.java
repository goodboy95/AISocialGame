package com.aisocialgame.repository.credit;

import com.aisocialgame.model.credit.CreditExchangeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CreditExchangeTransactionRepository extends JpaRepository<CreditExchangeTransaction, Long> {
    Optional<CreditExchangeTransaction> findByRequestId(String requestId);

    @Query("select coalesce(sum(t.projectTokens), 0) from CreditExchangeTransaction t " +
            "where t.userId = :userId and t.projectKey = :projectKey and t.status = 'SUCCESS' " +
            "and t.createdAt >= :start and t.createdAt < :end")
    long sumSuccessTokensBetween(@Param("userId") long userId,
                                 @Param("projectKey") String projectKey,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);
}

