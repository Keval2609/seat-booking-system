package com.dev.seatbooking.repository;

import com.dev.seatbooking.entity.Hold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HoldRepository extends JpaRepository<Hold, Long> {
}
