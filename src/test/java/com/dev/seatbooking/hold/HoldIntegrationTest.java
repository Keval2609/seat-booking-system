package com.dev.seatbooking.hold;

import com.dev.seatbooking.TestcontainersConfig;
import com.dev.seatbooking.entity.Role;
import com.dev.seatbooking.entity.Seat;
import com.dev.seatbooking.entity.SeatStatus;
import com.dev.seatbooking.entity.User;
import com.dev.seatbooking.repository.HoldRepository;
import com.dev.seatbooking.repository.SeatRepository;
import com.dev.seatbooking.repository.UserRepository;
import com.dev.seatbooking.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class HoldIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private HoldRepository holdRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcClient jdbc;

    private String tokenFor(Role role) {
        User user = userRepository.save(new User(UUID.randomUUID() + "@test.com", "hash", role));
        return jwtService.issue(user).accessToken();
    }

    private Long createEvent(String adminToken, Instant startsAt) throws Exception {
        String body = """
                {
                    "name": "Live Show",
                    "venue": "Metropolis Hall",
                    "startsAt": "%s"
                }
                """.formatted(startsAt);

        MvcResult result = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void bulkCreateSeats(String adminToken, Long eventId, int rows, int cols) throws Exception {
        String body = """
                {
                    "rows": %d,
                    "columns": %d,
                    "priceCents": 5000
                }
                """.formatted(rows, cols);

        mockMvc.perform(post("/api/events/" + eventId + "/seats/bulk")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private void assertInvariantQueriesClean() {
        // 1. Booked seats must correspond to an active booking item
        Long inv1 = jdbc.sql("""
                SELECT count(*) FROM seats s
                WHERE s.status = 'BOOKED'
                  AND NOT EXISTS (
                      SELECT 1 FROM booking_items bi
                      WHERE bi.seat_id = s.id AND bi.status = 'ACTIVE'
                  )
                """).query(Long.class).single();
        assertThat(inv1).isZero();

        // 2. Active booking items must point to booked seats
        Long inv2 = jdbc.sql("""
                SELECT count(*) FROM booking_items bi
                JOIN seats s ON s.id = bi.seat_id
                WHERE bi.status = 'ACTIVE' AND s.status <> 'BOOKED'
                """).query(Long.class).single();
        assertThat(inv2).isZero();

        // 3. Held seats must point to active holds
        Long inv3 = jdbc.sql("""
                SELECT count(*) FROM seats s
                JOIN holds h ON h.id = s.hold_id
                WHERE s.status = 'HELD' AND h.status <> 'ACTIVE'
                """).query(Long.class).single();
        assertThat(inv3).isZero();

        // 4. Active holds must be associated with at least one seat
        Long inv4 = jdbc.sql("""
                SELECT count(*) FROM holds h
                WHERE h.status = 'ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM seats s WHERE s.hold_id = h.id)
                """).query(Long.class).single();
        assertThat(inv4).isZero();
    }

    @Test
    @DisplayName("Hold seat 1 as user A: 201 with holdId and expiresAt; seat is HELD")
    void holdSeat_success() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String userToken = tokenFor(Role.USER);

        Long eventId = createEvent(adminToken, Instant.now().plus(Duration.ofDays(5)));
        bulkCreateSeats(adminToken, eventId, 2, 2);

        List<Seat> seats = seatRepository.findForEvent(eventId, List.of());
        List<Long> seatIds = seatRepository.findAll().stream()
                .filter(s -> s.getEventId().equals(eventId))
                .map(Seat::getId)
                .sorted()
                .toList();
        Long seatId = seatIds.getFirst();

        String body = """
                {
                    "seatIds": [%d]
                }
                """.formatted(seatId);

        MvcResult result = mockMvc.perform(post("/api/events/" + eventId + "/holds")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdId", notNullValue()))
                .andExpect(jsonPath("$.expiresAt", notNullValue()))
                .andReturn();

        Long holdId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.holdId")).longValue();

        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getHoldId()).isEqualTo(holdId);
        assertThat(seat.getVersion()).isEqualTo(1L);

        assertInvariantQueriesClean();
    }

    @Test
    @DisplayName("Hold seat 1 as user B when already held: 409 SEAT_UNAVAILABLE with rollback")
    void holdSeat_alreadyHeld_returns409SeatUnavailable_andRollsBack() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String userAToken = tokenFor(Role.USER);
        String userBToken = tokenFor(Role.USER);

        Long eventId = createEvent(adminToken, Instant.now().plus(Duration.ofDays(5)));
        bulkCreateSeats(adminToken, eventId, 2, 2);

        List<Long> seatIds = seatRepository.findAll().stream()
                .filter(s -> s.getEventId().equals(eventId))
                .map(Seat::getId)
                .sorted()
                .toList();
        Long targetSeat = seatIds.getFirst();

        // User A holds seat
        mockMvc.perform(post("/api/events/" + eventId + "/holds")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\": [" + targetSeat + "]}"))
                .andExpect(status().isCreated());

        long holdsCountBefore = holdRepository.count();

        // User B attempts to hold same seat
        mockMvc.perform(post("/api/events/" + eventId + "/holds")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\": [" + targetSeat + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("SEAT_UNAVAILABLE")))
                .andExpect(jsonPath("$.message", is("One or more seats are not available")))
                .andExpect(jsonPath("$.seatIds", hasSize(1)))
                .andExpect(jsonPath("$.seatIds[0]", is(targetSeat.intValue())));

        // Verify holds count unchanged (JdbcClient insert rolled back with transaction)
        assertThat(holdRepository.count()).isEqualTo(holdsCountBefore);
        assertInvariantQueriesClean();
    }

    @Test
    @DisplayName("Hold 7 seats: 400 VALIDATION_FAILED")
    void holdSeats_moreThan6_returns400ValidationFailed() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String userToken = tokenFor(Role.USER);

        Long eventId = createEvent(adminToken, Instant.now().plus(Duration.ofDays(5)));
        bulkCreateSeats(adminToken, eventId, 3, 3); // 9 seats

        List<Long> sevenSeats = seatRepository.findAll().stream()
                .filter(s -> s.getEventId().equals(eventId))
                .map(Seat::getId)
                .sorted()
                .limit(7)
                .toList();

        long holdsCountBefore = holdRepository.count();

        mockMvc.perform(post("/api/events/" + eventId + "/holds")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\": " + sevenSeats + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        assertThat(holdRepository.count()).isEqualTo(holdsCountBefore);
    }

    @Test
    @DisplayName("Hold with duplicate seats in payload: 400 VALIDATION_FAILED")
    void holdSeats_duplicates_returns400ValidationFailed() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String userToken = tokenFor(Role.USER);

        Long eventId = createEvent(adminToken, Instant.now().plus(Duration.ofDays(5)));
        bulkCreateSeats(adminToken, eventId, 2, 2);

        Long seatId = seatRepository.findAll().stream()
                .filter(s -> s.getEventId().equals(eventId))
                .map(Seat::getId)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/events/" + eventId + "/holds")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\": [" + seatId + ", " + seatId + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("Hold on a past event: 409 EVENT_CLOSED and rollback")
    void holdSeats_pastEvent_returns409EventClosed_andRollsBack() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String userToken = tokenFor(Role.USER);

        Long eventId = createEvent(adminToken, Instant.now().plus(Duration.ofDays(1)));
        bulkCreateSeats(adminToken, eventId, 2, 2);

        // Simulate time passing: update starts_at to the past
        jdbc.sql("UPDATE events SET starts_at = clock_timestamp() - interval '1 hour' WHERE id = :id")
                .param("id", eventId)
                .update();

        Long seatId = seatRepository.findAll().stream()
                .filter(s -> s.getEventId().equals(eventId))
                .map(Seat::getId)
                .findFirst()
                .orElseThrow();

        long holdsCountBefore = holdRepository.count();

        mockMvc.perform(post("/api/events/" + eventId + "/holds")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\": [" + seatId + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("EVENT_CLOSED")))
                .andExpect(jsonPath("$.message", is("Event has started")));

        assertThat(holdRepository.count()).isEqualTo(holdsCountBefore);
    }

    @Test
    @DisplayName("Hold on unknown event: 404 EVENT_NOT_FOUND")
    void holdSeats_unknownEvent_returns404EventNotFound() throws Exception {
        String userToken = tokenFor(Role.USER);

        mockMvc.perform(post("/api/events/999999999/holds")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\": [1, 2]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("EVENT_NOT_FOUND")));
    }

    @Test
    @DisplayName("Hold without auth: 401 UNAUTHORIZED")
    void holdSeats_noAuth_returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/events/1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\": [1]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Hold with seat not belonging to event: 400 VALIDATION_FAILED")
    void holdSeats_seatNotFoundInEvent_returns400ValidationFailed() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String userToken = tokenFor(Role.USER);

        Long event1 = createEvent(adminToken, Instant.now().plus(Duration.ofDays(5)));
        bulkCreateSeats(adminToken, event1, 2, 2);

        Long event2 = createEvent(adminToken, Instant.now().plus(Duration.ofDays(5)));
        bulkCreateSeats(adminToken, event2, 2, 2);

        Long seatFromEvent2 = seatRepository.findAll().stream()
                .filter(s -> s.getEventId().equals(event2))
                .map(Seat::getId)
                .findFirst()
                .orElseThrow();

        long holdsCountBefore = holdRepository.count();

        mockMvc.perform(post("/api/events/" + event1 + "/holds")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\": [" + seatFromEvent2 + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.message", is("Seat not found in event")));

        assertThat(holdRepository.count()).isEqualTo(holdsCountBefore);
    }
}
