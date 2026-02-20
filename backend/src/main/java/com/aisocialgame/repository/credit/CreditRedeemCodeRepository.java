package com.aisocialgame.repository.credit;

import com.aisocialgame.model.credit.CreditRedeemCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CreditRedeemCodeRepository extends JpaRepository<CreditRedeemCode, Long> {
    Optional<CreditRedeemCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CreditRedeemCode c where c.code = :code")
    Optional<CreditRedeemCode> findForUpdate(@Param("code") String code);
}

