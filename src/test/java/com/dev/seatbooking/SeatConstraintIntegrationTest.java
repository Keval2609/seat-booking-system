package com.dev.seatbooking;

import com.dev.seatbooking.entity.Event;
import com.dev.seatbooking.entity.Role;
import com.dev.seatbooking.entity.Seat;
import com.dev.seatbooking.entity.SeatStatus;
import com.dev.seatbooking.entity.User;
import com.dev.seatbooking.repository.EventRepository;
import com.dev.seatbooking.repository.SeatRepository;
import com.dev.seatbooking.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestcontainersConfig.class)
class SeatConstraintIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    @DisplayName("T11: Inserting a seat with status=HELD and holdId=null must violate check constraint and throw")
    void insertingSeatWithHeldStatusAndNullHoldIdThrows() {
        User user = userRepository.save(new User("organizer@example.com", "hash", Role.ADMIN));
        Event event = eventRepository.save(new Event("Rock Concert", "Arena", Instant.now().plusSeconds(3600), user.getId()));

        Seat invalidSeat = new Seat(event.getId(), "A1", 5000, SeatStatus.HELD, null);

        assertThatThrownBy(() -> seatRepository.saveAndFlush(invalidSeat))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
