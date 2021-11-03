package org.openbites.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public interface DistributedLock extends Lock {

    default void lock() {
        throw new UnsupportedOperationException();
    }

    default void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    boolean tryLock();

    default boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    void unlock();

    default Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}