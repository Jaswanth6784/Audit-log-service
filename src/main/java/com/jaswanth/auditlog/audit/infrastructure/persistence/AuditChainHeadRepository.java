package com.jaswanth.auditlog.audit.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuditChainHeadRepository extends JpaRepository<AuditChainHeadEntity, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select head from AuditChainHeadEntity head where head.chainId = :chainId")
    Optional<AuditChainHeadEntity> findByChainIdForUpdate(@Param("chainId") short chainId);
}
