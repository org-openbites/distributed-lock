package org.openbites.concurrent.locks.gcs;

public interface GcsLockListener {
    void acquiredLockException(Exception exception);

    void keepLockAliveException(Exception exception);

    void cleanupDeadLockException(Exception exception);
}