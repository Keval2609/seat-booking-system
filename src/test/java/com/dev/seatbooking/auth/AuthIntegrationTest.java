package com.dev.seatbooking.auth;

import com.dev.seatbooking.TestcontainersConfig;
import com.dev.seatbooking.auth.dto.TokenResponse;
import com.dev.seatbooking.entity.Role;
import com.dev.seatbooking.entity.User;
import com.dev.seatbooking.repository.UserRepository;
import com.dev.seatbooking.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    @DisplayName("A1: Register valid returns 201 with no passwordHash in body")
    void a1_registerValid() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String body = """
                {
                    "email": "%s",
                    "password": "validPassword123"
                }
                """.formatted(email);

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("A2: Register same email, different case returns 409 EMAIL_TAKEN")
    void a2_registerDuplicateEmailDifferentCaseReturns409() throws Exception {
        String email = UUID.randomUUID() + "@x.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace(email, email.toUpperCase(Locale.ROOT))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    @DisplayName("A3: 10 parallel registers, same email yields exactly 1 x 201, rest 409, zero 500")
    void a3_parallelRegistersSameEmail() throws Exception {
        String email = UUID.randomUUID() + "@parallel.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        int concurrency = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                        .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        int createdCount = 0;
        int conflictCount = 0;
        for (Future<Integer> future : futures) {
            int status = future.get();
            if (status == 201) {
                createdCount++;
            } else if (status == 409) {
                conflictCount++;
            }
        }

        assertThat(createdCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(concurrency - 1);
    }

    @Test
    @DisplayName("A4: Password under 8 chars returns 400 VALIDATION_FAILED")
    void a4_passwordUnder8CharsReturns400() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String body = """
                {
                    "email": "%s",
                    "password": "short"
                }
                """.formatted(email);

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("A5: Login correct returns 200 with token present")
    void a5_loginCorrect() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String password = "correctPassword123";

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber());
    }

    @Test
    @DisplayName("A6: Login wrong password, and unknown email both return 401 INVALID_CREDENTIALS with same body")
    void a6_loginWrongPasswordAndUnknownEmailBothReturn401() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String password = "correctPassword123";

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());

        MvcResult wrongPassResult = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"wrongPassword123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andReturn();

        String unknownEmail = UUID.randomUUID() + "@unknown.com";
        MvcResult unknownEmailResult = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + unknownEmail + "\",\"password\":\"wrongPassword123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andReturn();

        assertThat(wrongPassResult.getResponse().getContentAsString())
                .isEqualTo(unknownEmailResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("A7: No token on protected route returns 401 UNAUTHORIZED")
    void a7_noTokenOnProtectedRouteReturns401() throws Exception {
        mvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("A8: Tampered or expired token returns 401 UNAUTHORIZED")
    void a8_expiredOrTamperedTokenReturns401() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.minusSeconds(1))
                .claim("roles", List.of("USER"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        mvc.perform(post("/api/events")
                .header("Authorization", "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(post("/api/events")
                .header("Authorization", "Bearer " + expiredToken + "tampered")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("A9: USER token on POST /api/events returns 403 FORBIDDEN")
    void a9_userTokenOnPostEventsReturns403() throws Exception {
        User user = userRepository.save(new User(UUID.randomUUID() + "@user.com", "hash", Role.USER));
        TokenResponse token = jwtService.issue(user);

        mvc.perform(post("/api/events")
                .header("Authorization", "Bearer " + token.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A10: ADMIN token on POST /api/events is not 403")
    void a10_adminTokenOnPostEventsIsNot403() throws Exception {
        User admin = userRepository.save(new User(UUID.randomUUID() + "@admin.com", "hash", Role.ADMIN));
        TokenResponse token = jwtService.issue(admin);

        mvc.perform(post("/api/events")
                .header("Authorization", "Bearer " + token.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().is(not(403)));
    }
}
