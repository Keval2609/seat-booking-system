package com.dev.seatbooking.repository;

import com.dev.seatbooking.TestcontainersConfig;
import com.dev.seatbooking.entity.Event;
import com.dev.seatbooking.entity.Hold;
import com.dev.seatbooking.entity.HoldStatus;
import com.dev.seatbooking.entity.Role;
import com.dev.seatbooking.entity.Seat;
import com.dev.seatbooking.entity.SeatStatus;
import com.dev.seatbooking.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class SeatRepositoryCompareAndHoldTest {

    @Autowired private SeatRepository seatRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private HoldRepository holdRepository;
    @Autowired private JdbcClient jdbc;

    @AfterEach
    void clean() {
        jdbc.sql("TRUNCATE seats, holds, events, users RESTART IDENTITY CASCADE").update();
    }

    @Test
    @Transactional
    @DisplayName("compareAndHold succeeds when version matches, fails when version does not match")
    void testCompareAndHold() {
        User admin = userRepository.save(new User(UUID.randomUUID() + "@admin.com", "h", Role.ADMIN));
        Event event = eventRepository.save(new Event("E", "Venue", Instant.now().plusSeconds(3600), admin.getId()));
        Seat seat = seatRepository.save(new Seat(event.getId(), "A1", 1000, SeatStatus.AVAILABLE));
        Hold hold = holdRepository.save(new Hold(admin.getId(), event.getId(), HoldStatus.ACTIVE, Instant.now().plusSeconds(300)));

        Long holdId = hold.getId();
        Long currentVersion = seat.getVersion();

        // Stale CAS attempt
        int staleUpdated = seatRepository.compareAndHold(seat.getId(), currentVersion + 1, holdId);
        assertThat(staleUpdated).isZero();

        // Valid CAS attempt
        int updated = seatRepository.compareAndHold(seat.getId(), currentVersion, holdId);
        assertThat(updated).isEqualTo(1);

        Seat reloaded = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(reloaded.getHoldId()).isEqualTo(holdId);
        assertThat(reloaded.getVersion()).isEqualTo(currentVersion + 1);
    }
}
