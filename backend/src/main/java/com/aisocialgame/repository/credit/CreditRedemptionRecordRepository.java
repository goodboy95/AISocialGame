package com.aisocialgame.repository.credit;

import com.aisocialgame.model.credit.CreditRedemptionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface CreditRedemptionRecordRepository extends JpaRepository<CreditRedemptionRecord, Long> {
    boolean existsByUserIdAndProjectKeyAndCodeAndSuccessTrue(long userId, String projectKey, String code);

    Page<CreditRedemptionRecord> findByUserIdAndProjectKeyAndSuccessTrueOrderByIdDesc(long userId, String projectKey, Pageable pageable);

    long countByUserIdAndProjectKeyAndSuccessFalseAndCreatedAtBetween(long userId, String projectKey, LocalDateTime start, LocalDateTime end);
}

