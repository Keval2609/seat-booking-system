package com.dev.seatbooking.hold;

import com.dev.seatbooking.repository.SeatRepository;
import com.dev.seatbooking.strategy.OptimisticSeatLockingStrategy;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

public class BarrierOptimisticSeatLockingStrategy extends OptimisticSeatLockingStrategy {

    private final CyclicBarrier barrier = new CyclicBarrier(2);

    public BarrierOptimisticSeatLockingStrategy(SeatRepository repo) {
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
