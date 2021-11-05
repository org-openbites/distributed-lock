package org.openbites.concurrent.locks.gcs;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import org.openbites.concurrent.locks.DistributedLock;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobGetOption;
import com.google.cloud.storage.Storage.BlobSourceOption;
import com.google.cloud.storage.Storage.BlobTargetOption;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;

public class GcsLock implements DistributedLock, Serializable {

    private static final long serialVersionUID = 5184201915922962120L;

    private static final String LOCK_FILE_CONTENT     = "_lock";
    private static final String MIME_TYPE_TEXT_PLAIN  = "text/plain";
    private static final String CREATING_HOST         = "CREATING_HOST";
    private static final String TTL_EXTENSION_SECONDS = "TTL_EXTENSION_SECONDS";
    private static final String REFRESH_SECONDS       = "REFRESH_SECONDS";
    private static final String HOST_NAME             = getHostName();

    static final String LOCK_TTL_EPOCH_MS       = "LOCK_TTL_EPOCH_MS";
    static final int    GCS_PRECONDITION_FAILED = 412;

    private final GcsLockConfig        lockConfig;
    private final long                 intervalNanos;
    private final Storage              storage;
    private final Set<GcsLockListener> lockListeners = new HashSet<>();
    private final ReentrantLock        lock          = new ReentrantLock();

    private final KeepLockAlive   keepLockAlive   = new KeepLockAlive();
    private final CleanupDeadLock cleanupDeadLock = new CleanupDeadLock();

    private transient volatile Optional<Blob> acquired = Optional.empty();
    private transient volatile Thread         exclusiveOwnerThread;

    private transient Queue<Thread> waitingThreads = new ConcurrentLinkedQueue<>();

    public GcsLock(GcsLockConfig lockConfig) {
        if (Objects.isNull(lockConfig)) {
            throw new NullPointerException("Null GcsLockConfig");
        }
        this.lockConfig = lockConfig;
        this.storage = StorageOptions.getDefaultInstance().getService();
        intervalNanos = (long) (lockConfig.getRefreshIntervalInSeconds() * 1E9);
    }

    @Override
    public boolean tryLock() {
        try {
            Map<String, String> metadata = computeMetaData();
            BlobId              blobId   = BlobId.of(lockConfig.getGcsBucketName(), lockConfig.getGcsLockFilename());
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                                        .setMetadata(metadata)
                                        .setContentType(MIME_TYPE_TEXT_PLAIN)
                                        .build();
            Storage.BlobTargetOption blobOption = Storage.BlobTargetOption.doesNotExist();
            Blob                     blob       = storage.create(blobInfo, LOCK_FILE_CONTENT.getBytes(), blobOption);

            acquired = Optional.of(blob);
            exclusiveOwnerThread = Thread.currentThread();
            keepLockAlive.start();
            return true;
        } catch (Exception exception) {
            if ((exception instanceof StorageException) && ((StorageException) exception).getCode() == GCS_PRECONDITION_FAILED) {
                cleanupDeadLock.start();
                return false;
            }

            notifyAcquireLockListeners(exception);

            return false;
        }
    }

    @Override
    public void lock() {
        while (!tryLock()) {
            waitingThreads.add(Thread.currentThread());
            LockSupport.park();
            waitingThreads.remove(Thread.currentThread());
        }
    }

    @Override
    public void unlock() {
        lock.lock();
        try {
            if (isHeldByCurrentThread()) {
                acquired.ifPresent(blob -> {
                    acquired = Optional.empty();
                    exclusiveOwnerThread = null;
                    deleteLock(blob);
                });
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isLocked() {
        return acquired.isPresent();
    }

    public boolean isHeldByCurrentThread() {
        return exclusiveOwnerThread == Thread.currentThread();
    }

    public void addLockListener(GcsLockListener listener) {
        lockListeners.add(listener);
    }

    public void removeLockListener(GcsLockListener listener) {
        lockListeners.remove(listener);
    }

    private abstract class SingleIntervalExecution implements Runnable {

        Thread executingThread;

        void start() {
            lock.lock();
            try {
                if (executingThread == null) {
                    Thread thread = new Thread(this);
                    thread.setName(String.format("%s-%s-%s",
                                                 this.getClass().getSimpleName(),
                                                 lockConfig.getGcsBucketName(),
                                                 lockConfig.getGcsLockFilename()));
                    thread.setDaemon(true);
                    thread.start();
                    executingThread = thread;
                }
            } finally {
                lock.unlock();
            }
        }

        void finish() {
            executingThread = null;
        }
    }

    private class KeepLockAlive extends SingleIntervalExecution {

        @Override
        public void run() {
            while (true) {
                lock.lock();
                try {
                    Blob updatedBlob;
                    if (!acquired.isPresent() ||
                        Objects.isNull(updatedBlob = storage.get(lockConfig.getGcsBucketName(), lockConfig.getGcsLockFilename(),
                                                                 BlobGetOption.generationMatch(acquired.get().getGeneration()),
                                                                 BlobGetOption.metagenerationMatch(acquired.get().getMetageneration())))) {
                        finish();
                        return;
                    } else {
                        updatedBlob = updatedBlob.toBuilder().setMetadata(computeMetaData()).build();
                        updatedBlob = storage.update(updatedBlob, BlobTargetOption.generationMatch(), BlobTargetOption.metagenerationMatch());
                        acquired = Optional.of(updatedBlob);
                    }
                } catch (Exception exception) {
                    notifyKeepLockAliveListeners(exception);
                    finish();
                    return;
                } finally {
                    lock.unlock();
                }

                LockSupport.parkNanos(intervalNanos);
            }
        }
    }

    private class CleanupDeadLock extends SingleIntervalExecution {

        @Override
        public void run() {
            while (true) {
                LockSupport.parkNanos(intervalNanos);

                Blob blob = storage.get(lockConfig.getGcsBucketName(), lockConfig.getGcsLockFilename());

                if (Objects.isNull(blob)) {
                    finish();
                    return;
                }

                Map<String, String> metaData = blob.getMetadata();
                long ttl = Optional.ofNullable(metaData)
                                   .map(metadata -> metaData.get(LOCK_TTL_EPOCH_MS))
                                   .map(Long::valueOf)
                                   .orElse(Long.valueOf(Long.MAX_VALUE))
                                   .longValue();

                if (ttl <= System.currentTimeMillis()) {
                    try {
                        deleteLock(blob);
                    } catch (Exception exception) {
                        notifyCleanupDeadLockListeners(exception);
                    } finally {
                        finish();
                        return;
                    }
                }
            }
        }

        @Override
        void finish() {
            super.finish();

            Optional<Thread> thread = waitingThreads.stream().findAny();
            thread.ifPresent(LockSupport::unpark);

            if (waitingThreads.size() > 0) start();
        }
    }

    private Map<String, String> computeMetaData() {
        long                keepAliveToUnitMillis = System.currentTimeMillis() + lockConfig.getLifeExtensionInSeconds().intValue() * 1000L;
        Map<String, String> metaData              = new HashMap<>();
        metaData.put(LOCK_TTL_EPOCH_MS, String.valueOf(keepAliveToUnitMillis));
        metaData.put(CREATING_HOST, HOST_NAME);
        metaData.put(TTL_EXTENSION_SECONDS, String.valueOf(lockConfig.getLifeExtensionInSeconds()));
        metaData.put(REFRESH_SECONDS, String.valueOf(lockConfig.getRefreshIntervalInSeconds()));
        return metaData;
    }

    private void notifyAcquireLockListeners(Exception exception) {
        lockListeners.forEach(listener -> {
            try {
                listener.acquiredLockException(exception);
            } catch (Exception e) {
                // Don't care if listener throws any exception
            }
        });
    }

    private void notifyKeepLockAliveListeners(Exception exception) {
        lockListeners.forEach(listener -> {
            try {
                listener.keepLockAliveException(exception);
            } catch (Exception e) {
                // Don't care if listener throws any exception
            }
        });
    }

    private void notifyCleanupDeadLockListeners(Exception exception) {
        lockListeners.forEach(listener -> {
            try {
                listener.cleanupDeadLockException(exception);
            } catch (Exception e) {
                // Don't care if listener throws any exception
            }
        });
    }

    private void deleteLock(Blob blob) {
        storage.delete(blob.getBlobId(),
                       BlobSourceOption.generationMatch(blob.getGeneration()),
                       BlobSourceOption.metagenerationMatch(blob.getMetageneration()));
    }

    private static String getHostName() {
        String hostName = "NONE";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
        }

        return hostName;
    }
}