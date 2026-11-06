package com.shop.repository;

import com.shop.model.PendingApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PendingApprovalRepository
        extends JpaRepository<PendingApproval, Long> {

    Optional<PendingApproval> findByTokenHash(String tokenHash);

    void deleteByEmailAndType(String email, String type);
}