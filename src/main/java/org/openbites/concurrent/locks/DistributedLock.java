package org.openbites.concurrent.locks;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * An interface adapted from {@link Lock Lock}
 */
public interface DistributedLock extends Lock {

    default void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    default Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}