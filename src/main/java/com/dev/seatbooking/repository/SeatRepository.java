package com.dev.seatbooking.repository;

import com.dev.seatbooking.dto.SeatResponse;
import com.dev.seatbooking.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    boolean existsByEventId(Long eventId);

    long countByEventId(Long eventId);

    @Modifying
    @Query(value = """
            INSERT INTO seats (event_id, label, price_cents)
            SELECT :eventId, chr(64 + r.n) || c.n, :price
            FROM generate_series(1, :rows) AS r(n)
            CROSS JOIN generate_series(1, :cols) AS c(n)
            ORDER BY r.n, c.n
            """, nativeQuery = true)
    int bulkInsert(@Param("eventId") Long eventId,
                   @Param("rows") int rows,
                   @Param("cols") int cols,
                   @Param("price") int price);

    @Query("""
            select new com.dev.seatbooking.dto.SeatResponse(s.id, s.label, s.priceCents, s.status)
            from Seat s where s.eventId = :eventId order by s.id
            """)
    List<SeatResponse> findViewsByEventId(@Param("eventId") Long eventId);

    @Query("select s from Seat s where s.eventId = :eventId and s.id in :ids order by s.id")
    List<Seat> findForEvent(@Param("eventId") Long eventId, @Param("ids") List<Long> ids);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE seats
            SET status = 'HELD', hold_id = :holdId, version = version + 1
            WHERE id IN (:ids)
            """, nativeQuery = true)
    int blindHold(@Param("holdId") Long holdId, @Param("ids") List<Long> ids);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE seats
            SET status = 'HELD', hold_id = :holdId, version = version + 1
            WHERE id = :id AND version = :version
            """, nativeQuery = true)
    int compareAndHold(@Param("id") Long id,
                       @Param("version") Long version,
                       @Param("holdId") Long holdId);
}
