package com.portfolio.ledger.util;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class IdempotencyLocks {
    private static final int STRIPE_COUNT = 256;
    private final ReentrantLock[] stripes = new ReentrantLock[STRIPE_COUNT];

    public IdempotencyLocks() {
        for (int index = 0; index < stripes.length; index++) {
            stripes[index] = new ReentrantLock();
        }
    }

    public <T> T withLock(String key, Supplier<T> operation) {
        ReentrantLock lock = stripes[Math.floorMod(key.hashCode(), stripes.length)];
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }
}
