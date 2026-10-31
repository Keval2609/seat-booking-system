package com.dev.seatbooking.hold;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "booking.locking-strategy=PESSIMISTIC")
class PessimisticConcurrentHoldTest extends AbstractConcurrentHoldTest {}
