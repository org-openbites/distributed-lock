package org.openbites.concurrent.locks;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * An interface adapted from {@link Lock Lock}
 */
public interface DistributedLock extends Lock {

    /**
     * Not supported by default
     * @throws UnsupportedOperationException
     */
    default Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}