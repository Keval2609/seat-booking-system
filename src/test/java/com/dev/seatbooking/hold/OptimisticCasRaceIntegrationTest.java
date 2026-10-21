package com.dev.seatbooking.hold;

import com.dev.seatbooking.TestcontainersConfig;
import com.dev.seatbooking.entity.Event;
import com.dev.seatbooking.entity.Role;
import com.dev.seatbooking.entity.Seat;
import com.dev.seatbooking.entity.SeatStatus;
import com.dev.seatbooking.entity.User;
import com.dev.seatbooking.error.OptimisticConflictException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "booking.locking-strategy=OPTIMISTIC")
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, OptimisticCasRaceIntegrationTest.BarrierConfig.class})
class OptimisticCasRaceIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class BarrierConfig {
        @Bean
        @Primary
        BarrierOptimisticSeatLockingStrategy barrierStrategy(SeatRepository repo) {
            return new BarrierOptimisticSeatLockingStrategy(repo);
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
    @DisplayName("T8b: OPTIMISTIC with forced interleaving yields one winner, one CAS loser")
    void casLoserRollsBack() throws Exception {
        User admin = users.save(new User(UUID.randomUUID() + "@o.com", "h", Role.ADMIN));
        User a = users.save(new User(UUID.randomUUID() + "@o.com", "h", Role.USER));
        User b = users.save(new User(UUID.randomUUID() + "@o.com", "h", Role.USER));
        Event event = events.save(new Event("O", "Arena", Instant.now().plusSeconds(3600), admin.getId()));
        Seat seat = seats.save(new Seat(event.getId(), "A1", 1000, SeatStatus.AVAILABLE));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<String>> tasks = List.of(
                () -> attempt(a.getId(), event.getId(), seat.getId()),
                () -> attempt(b.getId(), event.getId(), seat.getId()));

        List<String> results = new ArrayList<>();
        for (Future<String> f : pool.invokeAll(tasks)) {
            results.add(f.get());
        }
        pool.shutdown();

        assertThat(results).containsExactlyInAnyOrder("WIN", "CAS_LOSS");
        assertThat(count("SELECT count(*) FROM holds WHERE status = 'ACTIVE'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM holds")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM seats WHERE status = 'HELD'")).isEqualTo(1);
    }

    private String attempt(Long userId, Long eventId, Long seatId) {
        try {
            holdService.hold(userId, eventId, List.of(seatId));
            return "WIN";
        } catch (OptimisticConflictException e) {
            return "CAS_LOSS";
        }
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
