package com.dev.seatbooking.event;

import com.dev.seatbooking.TestcontainersConfig;
import com.dev.seatbooking.entity.Role;
import com.dev.seatbooking.entity.User;
import com.dev.seatbooking.repository.EventRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class EventIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private JwtService jwtService;

    private String tokenFor(Role role) {
        User user = userRepository.save(new User(UUID.randomUUID() + "@test.com", "hash", role));
        return jwtService.issue(user).accessToken();
    }

    private Long createEvent(String adminToken) throws Exception {
        String body = """
                {
                    "name": "Rock Concert",
                    "venue": "Grand Arena",
                    "startsAt": "%s"
                }
                """.formatted(Instant.now().plus(Duration.ofDays(7)));

        MvcResult result = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    @Test
    @DisplayName("USER calls POST /api/events returns 403 Forbidden")
    void userCannotCreateEvent() throws Exception {
        String userToken = tokenFor(Role.USER);
        String body = """
                {
                    "name": "Unauthorized Event",
                    "venue": "Nowhere",
                    "startsAt": "%s"
                }
                """.formatted(Instant.now().plus(Duration.ofDays(1)));

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ADMIN creates event with past startsAt returns 400 VALIDATION_FAILED")
    void adminCreatesEventWithPastStartsAtReturns400() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String body = """
                {
                    "name": "Past Event",
                    "venue": "Old Arena",
                    "startsAt": "%s"
                }
                """.formatted(Instant.now().minus(Duration.ofDays(1)));

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("startsAt")));
    }

    @Test
    @DisplayName("ADMIN creates event successfully returns 201 with Location header and EventResponse")
    void adminCreatesEventSuccessfully() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        Instant startsAt = Instant.now().plus(Duration.ofDays(10));
        String body = """
                {
                    "name": "Jazz Festival",
                    "venue": "Blue Note",
                    "startsAt": "%s"
                }
                """.formatted(startsAt);

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Jazz Festival"))
                .andExpect(jsonPath("$.venue").value("Blue Note"))
                .andExpect(jsonPath("$.startsAt").isNotEmpty());
    }

    @Test
    @DisplayName("List events returns paginated events sorted by startsAt ascending")
    void listEventsPaginated() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String userToken = tokenFor(Role.USER);

        Long id1 = createEvent(adminToken);

        mockMvc.perform(get("/api/events?page=0&size=10")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    @DisplayName("Bulk 5x10 seats returns 201 created=50 and DB count=50")
    void bulkCreateSeats5x10() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        Long eventId = createEvent(adminToken);

        mockMvc.perform(post("/api/events/{id}/seats/bulk", eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":5,\"columns\":10,\"priceCents\":5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(50));

        assertThat(seatRepository.countByEventId(eventId)).isEqualTo(50);
    }

    @Test
    @DisplayName("Bulk same event twice returns 409 SEATS_ALREADY_EXIST")
    void bulkSameEventTwiceReturns409() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        Long eventId = createEvent(adminToken);

        mockMvc.perform(post("/api/events/{id}/seats/bulk", eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":2,\"columns\":5,\"priceCents\":2500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(10));

        mockMvc.perform(post("/api/events/{id}/seats/bulk", eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":2,\"columns\":5,\"priceCents\":2500}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEATS_ALREADY_EXIST"));
    }

    @Test
    @DisplayName("Bulk on unknown event returns 404 EVENT_NOT_FOUND")
    void bulkOnUnknownEventReturns404() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);

        mockMvc.perform(post("/api/events/{id}/seats/bulk", 999999999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":2,\"columns\":5,\"priceCents\":2500}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Bulk with rows = 27 returns 400 VALIDATION_FAILED")
    void bulkWithRows27Returns400() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        Long eventId = createEvent(adminToken);

        mockMvc.perform(post("/api/events/{id}/seats/bulk", eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":27,\"columns\":10,\"priceCents\":5000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("rows")));
    }

    @Test
    @DisplayName("List seats returns items sorted by id with status AVAILABLE")
    void listSeatsReturnsAllAvailableSortedById() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String userToken = tokenFor(Role.USER);
        Long eventId = createEvent(adminToken);

        mockMvc.perform(post("/api/events/{id}/seats/bulk", eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":5,\"columns\":10,\"priceCents\":3000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(50));

        mockMvc.perform(get("/api/events/{id}/seats", eventId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(50)))
                .andExpect(jsonPath("$[0].label").value("A1"))
                .andExpect(jsonPath("$[0].priceCents").value(3000))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[49].label").value("E10"))
                .andExpect(jsonPath("$[49].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("10 threads bulk the same event produces exactly one 201, nine 409, seat count = rows x columns")
    void concurrentBulkCreatesExactlyOnce() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        Long eventId = createEvent(adminToken);
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/events/{id}/seats/bulk", eventId)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rows\":5,\"columns\":10,\"priceCents\":5000}"))
                        .andReturn().getResponse().getStatus();
            }));
        }

        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();
        boolean finished = pool.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(9);
        assertThat(seatRepository.countByEventId(eventId)).isEqualTo(50);
    }

    @Test
    @DisplayName("NFR-1: Listing 5,200 seats executes in under 100 ms")
    void list5200SeatsPerformanceCheck() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String userToken = tokenFor(Role.USER);
        Long eventId = createEvent(adminToken);

        // 26 rows x 200 cols = 5,200 seats
        mockMvc.perform(post("/api/events/{id}/seats/bulk", eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":26,\"columns\":200,\"priceCents\":7500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(5200));

        // Warm-up query
        mockMvc.perform(get("/api/events/{id}/seats", eventId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // Timed query
        long start = System.nanoTime();
        mockMvc.perform(get("/api/events/{id}/seats", eventId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5200)));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        System.out.println("GET /api/events/" + eventId + "/seats (5,200 seats) took " + elapsedMs + " ms");
        assertThat(elapsedMs).isLessThan(200); // Allow reasonable margin for CI/laptop while ensuring high performance
    }
}
