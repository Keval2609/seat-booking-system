package com.dev.seatbooking.repository;

import com.dev.seatbooking.entity.IdempotencyKey;
import com.dev.seatbooking.entity.IdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {
}
