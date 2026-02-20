package com.aisocialgame.repository.credit;

import com.aisocialgame.model.credit.CreditAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {
    Optional<CreditAccount> findByUserIdAndProjectKey(long userId, String projectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditAccount a where a.userId = :userId and a.projectKey = :projectKey")
    Optional<CreditAccount> findForUpdate(@Param("userId") long userId, @Param("projectKey") String projectKey);
}

