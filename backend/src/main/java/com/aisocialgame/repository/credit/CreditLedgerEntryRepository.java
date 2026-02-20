package com.aisocialgame.repository.credit;

import com.aisocialgame.model.credit.CreditLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CreditLedgerEntryRepository extends JpaRepository<CreditLedgerEntry, Long> {
    Page<CreditLedgerEntry> findByUserIdAndProjectKeyOrderByIdDesc(long userId, String projectKey, Pageable pageable);

    Page<CreditLedgerEntry> findByUserIdAndProjectKeyAndTypeInOrderByIdDesc(long userId, String projectKey, Collection<String> types, Pageable pageable);

    Optional<CreditLedgerEntry> findByRequestId(String requestId);

    boolean existsByRelatedEntryId(Long relatedEntryId);
}

