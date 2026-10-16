package com.dev.seatbooking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class SeatBookingSystemApplicationTests {

    @Test
    void contextLoads() {
    }

}
