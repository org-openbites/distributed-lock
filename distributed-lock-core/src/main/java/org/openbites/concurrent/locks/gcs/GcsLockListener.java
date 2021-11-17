package org.openbites.concurrent.locks.gcs;

public interface GcsLockListener {

    /**
     * Callback for when exception has been encountered during lock acquisition
     * @param exception: The exception encountered during lock acquisition
     */
    void acquireLockException(Exception exception);

    /**
     * Callback for when exception has been encountered during lock release
     * @param exception: The exception encountered during lock release
     */
    void releaseLockException(Exception exception);

    /**
     * Callback for when exception has been encountered while keeping the lock alive
     * @param exception: The exception encountered while keeping the lock alive
     */
    void keepLockAliveException(Exception exception);

    /**
     * Callback for when exception has been encountered while cleaning up the expired lock
     * @param exception: The exception encountered while cleaning up the expired lock
     */
    void cleanupDeadLockException(Exception exception);
}