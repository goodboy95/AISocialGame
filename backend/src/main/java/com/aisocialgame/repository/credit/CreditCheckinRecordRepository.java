package com.aisocialgame.repository.credit;

import com.aisocialgame.model.credit.CreditCheckinRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CreditCheckinRecordRepository extends JpaRepository<CreditCheckinRecord, Long> {
    Optional<CreditCheckinRecord> findByUserIdAndProjectKeyAndCheckinDate(long userId, String projectKey, LocalDate checkinDate);

    Optional<CreditCheckinRecord> findTopByUserIdAndProjectKeyOrderByCheckinDateDesc(long userId, String projectKey);
}

