package com.dev.seatbooking.service;

import com.dev.seatbooking.dto.HoldResponse;
import com.dev.seatbooking.error.ApiException;
import com.dev.seatbooking.error.EventClosedException;
import com.dev.seatbooking.error.ValidationFailedException;
import com.dev.seatbooking.strategy.SeatLockingStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class HoldService {

    private final JdbcClient jdbc;
    private final SeatLockingStrategy strategy;
    private final long ttlSeconds;

    public HoldService(JdbcClient jdbc,
                       SeatLockingStrategy strategy,
                       @Value("${booking.hold-ttl-seconds:300}") long ttlSeconds) {
        this.jdbc = jdbc;
        this.strategy = strategy;
        this.ttlSeconds = ttlSeconds;
    }

    @Transactional
    public HoldResponse hold(Long userId, Long eventId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty() || seatIds.size() > 6) {
            throw new ValidationFailedException("1 to 6 distinct seat ids required");
        }
        List<Long> sorted = seatIds.stream().distinct().sorted().toList();
        if (sorted.size() != seatIds.size()) {
            throw new ValidationFailedException("1 to 6 distinct seat ids required");
        }

        Boolean open = jdbc.sql("SELECT starts_at > clock_timestamp() FROM events WHERE id = :id")
                .param("id", eventId)
                .query(Boolean.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!open) {
            throw new EventClosedException();
        }

        HoldResponse created = jdbc.sql("""
                INSERT INTO holds (user_id, event_id, status, expires_at)
                VALUES (:userId, :eventId, 'ACTIVE', clock_timestamp() + (:ttl * interval '1 second'))
                RETURNING id, expires_at
                """)
                .param("userId", userId)
                .param("eventId", eventId)
                .param("ttl", ttlSeconds)
                .query((rs, n) -> new HoldResponse(
                        rs.getLong("id"),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant()))
                .single();

        strategy.acquire(created.holdId(), eventId, sorted);
        return created;
    }

    @Transactional
    public void release(Long userId, Long holdId) {
        int won = jdbc.sql("""
                UPDATE holds SET status = 'RELEASED'
                WHERE id = :id AND user_id = :userId AND status = 'ACTIVE'
                """)
                .param("id", holdId).param("userId", userId).update();

        if (won == 0) {
            var row = jdbc.sql("SELECT user_id, status FROM holds WHERE id = :id")
                    .param("id", holdId)
                    .query((rs, n) -> new Object[]{rs.getLong("user_id"), rs.getString("status")})
                    .optional()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "Hold not found"));
            if (!userId.equals(row[0])) {
                throw new ApiException(HttpStatus.FORBIDDEN, "HOLD_NOT_OWNED", "Hold not owned by user");
            }
            if ("EXPIRED".equals(row[1])) {
                throw new ApiException(HttpStatus.CONFLICT, "HOLD_EXPIRED", "Hold expired");
            }
            throw new ApiException(HttpStatus.CONFLICT, "HOLD_NOT_ACTIVE", "Hold is not active");
        }

        jdbc.sql("""
                UPDATE seats SET status = 'AVAILABLE', hold_id = NULL, version = version + 1
                WHERE hold_id = :id AND status = 'HELD'
                """)
                .param("id", holdId).update();
    }
}
