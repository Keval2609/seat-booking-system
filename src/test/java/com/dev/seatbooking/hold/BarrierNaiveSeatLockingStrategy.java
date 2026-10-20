package com.dev.seatbooking.hold;

import com.dev.seatbooking.repository.SeatRepository;
import com.dev.seatbooking.strategy.NaiveSeatLockingStrategy;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

public class BarrierNaiveSeatLockingStrategy extends NaiveSeatLockingStrategy {

    private final CyclicBarrier barrier = new CyclicBarrier(2);

    public BarrierNaiveSeatLockingStrategy(SeatRepository repo) {
        super(repo);
    }

    @Override
    protected void afterRead() {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Barrier failed", e);
        }
    }
}
