package org.openbites.concurrent.locks.gcs;

public interface GcsLockListener {
    void keepLockAliveException(Exception exception);

    void cleanupDeadLockException(Exception exception);
}