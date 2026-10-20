package com.dev.seatbooking.hold;

import com.dev.seatbooking.TestcontainersConfig;
import com.dev.seatbooking.entity.Event;
import com.dev.seatbooking.entity.Role;
import com.dev.seatbooking.entity.Seat;
import com.dev.seatbooking.entity.SeatStatus;
import com.dev.seatbooking.entity.User;
import com.dev.seatbooking.error.SeatUnavailableException;
import com.dev.seatbooking.repository.EventRepository;
import com.dev.seatbooking.repository.SeatRepository;
import com.dev.seatbooking.repository.UserRepository;
import com.dev.seatbooking.service.HoldService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, NaiveRaceIntegrationTest.BarrierConfig.class})
class NaiveRaceIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class BarrierConfig {
        @Bean
        @Primary
        BarrierNaiveSeatLockingStrategy barrierStrategy(SeatRepository repo) {
            return new BarrierNaiveSeatLockingStrategy(repo);
        }
    }

    @Autowired HoldService holdService;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired SeatRepository seats;
    @Autowired JdbcClient jdbc;

    @AfterEach
    void clean() {
        jdbc.sql("TRUNCATE seats, holds, events, users RESTART IDENTITY CASCADE").update();
    }

    @Test
    @DisplayName("T8: NAIVE with forced interleaving double-holds one seat")
    void naiveStrategyDoubleHolds() throws Exception {
        User admin = users.save(new User(UUID.randomUUID() + "@t8.com", "h", Role.ADMIN));
        User a = users.save(new User(UUID.randomUUID() + "@t8.com", "h", Role.USER));
        User b = users.save(new User(UUID.randomUUID() + "@t8.com", "h", Role.USER));
        Event event = events.save(new Event("T8", "Arena", Instant.now().plusSeconds(3600), admin.getId()));
        Seat seat = seats.save(new Seat(event.getId(), "A1", 1000, SeatStatus.AVAILABLE));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Boolean>> tasks = List.of(
                () -> tryHold(a.getId(), event.getId(), seat.getId()),
                () -> tryHold(b.getId(), event.getId(), seat.getId()));

        long wins = 0;
        for (Future<Boolean> f : pool.invokeAll(tasks)) {
            if (f.get()) wins++;
        }
        pool.shutdown();

        Long orphanHolds = jdbc.sql("""
                SELECT count(*) FROM holds h
                WHERE h.status = 'ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM seats s WHERE s.hold_id = h.id)
                """).query(Long.class).single();
        Long activeHolds = jdbc.sql("SELECT count(*) FROM holds WHERE status = 'ACTIVE'")
                .query(Long.class).single();

        assertThat(wins).isEqualTo(2);
        assertThat(activeHolds).isEqualTo(2);
        assertThat(orphanHolds).isEqualTo(1);
    }

    private boolean tryHold(Long userId, Long eventId, Long seatId) {
        try {
            holdService.hold(userId, eventId, List.of(seatId));
            return true;
        } catch (SeatUnavailableException e) {
            return false;
        }
    }
}
