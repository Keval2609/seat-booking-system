package com.dev.seatbooking.hold;

import com.dev.seatbooking.TestcontainersConfig;
import com.dev.seatbooking.entity.Role;
import com.dev.seatbooking.entity.User;
import com.dev.seatbooking.repository.UserRepository;
import com.dev.seatbooking.security.JwtService;
import com.dev.seatbooking.strategy.SeatLockingStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
abstract class AbstractConcurrentHoldTest {

    record Outcome(int status, String body) {}

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired JdbcClient jdbc;
    @Autowired SeatLockingStrategy strategy;
    @Autowired PlatformTransactionManager txManager;

    @AfterEach
    void clean() {
        jdbc.sql("TRUNCATE seats, holds, events, users RESTART IDENTITY CASCADE").update();
    }

    String token(Role role) {
        User u = users.save(new User(UUID.randomUUID() + "@c.com", "h", role));
        return jwt.issue(u).accessToken();
    }

    Long newEvent(int seatCount) {
        Long admin = users.save(new User(UUID.randomUUID() + "@a.com", "h", Role.ADMIN)).getId();
        Long eventId = jdbc.sql("""
                INSERT INTO events (name, venue, starts_at, created_by)
                VALUES ('c', 'v', clock_timestamp() + interval '7 days', :a) RETURNING id
                """).param("a", admin).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO seats (event_id, label, price_cents)
                SELECT :e, 'S' || n, 1000 FROM generate_series(1, :n) AS g(n)
                """).param("e", eventId).param("n", seatCount).update();
        return eventId;
    }

    List<Long> seatIds(Long eventId) {
        return jdbc.sql("SELECT id FROM seats WHERE event_id = :e ORDER BY id")
                .param("e", eventId).query(Long.class).list();
    }

    long count(String sql, Object... args) {
        if (args == null || args.length == 0) {
            return jdbc.sql(sql).query(Long.class).single();
        }
        return jdbc.sql(sql).params(args).query(Long.class).single();
    }

    Outcome hold(String token, Long eventId, List<Long> seatIds) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/events/" + eventId + "/holds")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seatIds\":" + seatIds + "}")).andReturn();
        return new Outcome(r.getResponse().getStatus(), r.getResponse().getContentAsString());
    }

    List<Outcome> race(List<Callable<Outcome>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (Callable<Outcome> t : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return t.call();
            }));
        }
        ready.await();
        go.countDown();
        List<Outcome> out = new ArrayList<>();
        for (Future<Outcome> f : futures) {
            out.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return out;
    }

    <T> List<T> raceRaw(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> t : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return t.call();
            }));
        }
        ready.await();
        go.countDown();
        List<T> out = new ArrayList<>();
        for (Future<T> f : futures) {
            out.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return out;
    }

    void assertHoldInvariants() {
        assertThat(count("""
                SELECT count(*) FROM seats s JOIN holds h ON h.id = s.hold_id
                WHERE s.status = 'HELD' AND h.status <> 'ACTIVE'
                """)).isZero();
        assertThat(count("""
                SELECT count(*) FROM holds h
                WHERE h.status = 'ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM seats s WHERE s.hold_id = h.id)
                """)).isZero();
    }

    private Long createHoldInTx(Long userId, Long eventId) {
        return jdbc.sql("""
                INSERT INTO holds (user_id, event_id, status, expires_at)
                VALUES (:u, :e, 'ACTIVE', clock_timestamp() + interval '5 minutes')
                RETURNING id
                """)
                .param("u", userId)
                .param("e", eventId)
                .query(Long.class)
                .single();
    }

    private boolean holdDirectInTx(Long userId, Long eventId, List<Long> ids) {
        try {
            new TransactionTemplate(txManager).execute(status -> {
                Long holdId = createHoldInTx(userId, eventId);
                strategy.acquire(holdId, eventId, ids);
                return null;
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void t1_hundredThreadsOneSeat() throws Exception {
        Long eventId = newEvent(1);
        Long seat = seatIds(eventId).getFirst();
        List<Callable<Outcome>> tasks = IntStream.range(0, 100)
                .mapToObj(i -> token(Role.USER))
                .map(t -> (Callable<Outcome>) () -> hold(t, eventId, List.of(seat)))
                .toList();

        List<Outcome> out = race(tasks);

        assertThat(out.stream().filter(o -> o.status() == 201).count()).isEqualTo(1);
        assertThat(out.stream().filter(o -> o.status() == 409).count()).isEqualTo(99);
        assertThat(count("SELECT count(*) FROM holds WHERE event_id = ? AND status = 'ACTIVE'", eventId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM seats WHERE event_id = ? AND status = 'HELD'", eventId)).isEqualTo(1);
        assertHoldInvariants();
    }

    @Test
    void t2_twoHundredThreadsFiftySeats() throws Exception {
        Long eventId = newEvent(50);
        List<Long> all = seatIds(eventId);
        Random rnd = new Random(42);
        List<List<Long>> picks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            List<Long> shuffled = new ArrayList<>(all);
            Collections.shuffle(shuffled, rnd);
            picks.add(List.copyOf(shuffled.subList(0, 1 + rnd.nextInt(3))));
        }
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            String t = token(Role.USER);
            List<Long> pick = picks.get(i);
            tasks.add(() -> hold(t, eventId, pick));
        }

        List<Outcome> out = race(tasks);

        int wins = 0;
        int seatsExpected = 0;
        for (int i = 0; i < 200; i++) {
            int s = out.get(i).status();
            assertThat(s).isIn(201, 409);
            if (s == 201) {
                wins++;
                seatsExpected += picks.get(i).size();
            }
        }
        assertThat(count("SELECT count(*) FROM seats WHERE event_id = ? AND status = 'HELD'", eventId))
                .isEqualTo(seatsExpected);
        assertThat(count("SELECT count(*) FROM holds WHERE event_id = ? AND status = 'ACTIVE'", eventId))
                .isEqualTo(wins);
        assertHoldInvariants();
    }

    @RepeatedTest(50)
    void t3_oppositeOrderNoDeadlock() throws Exception {
        Long eventId = newEvent(6);
        List<Long> forward = seatIds(eventId).subList(0, 4);
        List<Long> reversed = new ArrayList<>(forward);
        Collections.reverse(reversed);
        String a = token(Role.USER);
        String b = token(Role.USER);

        List<Outcome> out = race(List.of(
                () -> hold(a, eventId, forward),
                () -> hold(b, eventId, reversed)));

        assertThat(out).noneMatch(o -> o.status() >= 500);
        assertThat(out.stream().filter(o -> o.status() == 201).count()).isEqualTo(1);
        assertThat(out.stream().filter(o -> o.status() == 409).count()).isEqualTo(1);
        assertHoldInvariants();
    }

    @RepeatedTest(20)
    void t3b_strategyDirectReverseOrderNoDeadlock() throws Exception {
        Long eventId = newEvent(4);
        List<Long> all = seatIds(eventId);
        List<Long> forward = List.of(all.get(0), all.get(1));
        List<Long> reversed = List.of(all.get(1), all.get(0));

        Long userA = users.save(new User(UUID.randomUUID() + "@a.com", "h", Role.USER)).getId();
        Long userB = users.save(new User(UUID.randomUUID() + "@b.com", "h", Role.USER)).getId();

        List<Callable<Boolean>> tasks = List.of(
                () -> holdDirectInTx(userA, eventId, forward),
                () -> holdDirectInTx(userB, eventId, reversed));

        List<Boolean> out = raceRaw(tasks);

        assertThat(out.stream().filter(b -> b).count()).isEqualTo(1);
        assertThat(out.stream().filter(b -> !b).count()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM seats WHERE event_id = ? AND status = 'HELD'", eventId)).isEqualTo(2);
        assertHoldInvariants();
    }
}
