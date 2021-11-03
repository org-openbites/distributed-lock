package org.openbites.concurrent.locks.gcs;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openbites.concurrent.locks.gcs.GcsLock.GCS_PRECONDITION_FAILED;
import static org.openbites.concurrent.locks.gcs.GcsLock.LOCK_TIME_TO_LIVE_EPOCH_MS;

import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobSourceOption;
import com.google.cloud.storage.Storage.BlobTargetOption;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;

public class GcsLockTest {

    private static final String LOCK_RETRIEVAL_EXCEPTION = "Lock Retrieval Exception";
    private static final String LOCK_DELETION_EXCEPTION  = "Lock Deletion Exception";

    private StorageOptions      storageOptions;
    private Storage             storage;
    private Blob                blob;
    private Blob.Builder        blobBuilder;
    private Map<String, String> metaData;

    private GcsLockListener           lifecycleListener;
    private ArgumentCaptor<Exception> exceptionArgumentCaptor;
    private StorageException          storageException = new StorageException(GCS_PRECONDITION_FAILED, "PRECONDITION DOESN't MATCH");
    private GcsLockConfig             configuration;

    MockedStatic<StorageOptions> storageOptionsMockedStatic;

    @Before
    public void setUp() {
        mockObjects();

        storageOptionsMockedStatic = mockStatic(StorageOptions.class);
        storageOptionsMockedStatic.when(StorageOptions::getDefaultInstance).thenReturn(storageOptions);
        when(storageOptions.getService()).thenReturn(storage);

        lifecycleListener = mock(GcsLockListener.class);
        exceptionArgumentCaptor = forClass(Exception.class);

        blob = mock(Blob.class);
        when(blob.toBuilder()).thenReturn(blobBuilder);
        when(blob.getMetadata()).thenReturn(metaData);

        when(blobBuilder.setMetadata(any())).thenReturn(blobBuilder);
        when(blobBuilder.build()).thenReturn(blob);

        configuration = GcsLockConfig.newBuilder()
                                     .setGcsBucketName("test-bucket")
                                     .setGcsLockFilename("test-lock")
                                     .setRefreshIntervalInSeconds(Integer.valueOf(10))
                                     .setTimeToLiveInSeconds(Integer.valueOf(60))
                                     .build();
    }

    @After
    public void tearDown() {
        storageOptionsMockedStatic.close();
    }

    /**
     * test the scenario when exception is encountered during the lock keep alive process after being successfully acquired.
     *
     * the exception could be due to deletion or recreation of the lock file by some other process when this process failed to keep it alive in a
     * timely fashion
     */
    @Test
    public void testKeepAliveDeletedLock() {
        when(storage.create(any(), (byte[]) any(), (Storage.BlobTargetOption) any())).thenReturn(blob);
        when(storage.get(anyString(), anyString(), any(), any())).thenReturn(null);

        GcsLock gcsLock = new GcsLock(configuration);
        gcsLock.addLockListener(lifecycleListener);

        if (gcsLock.tryLock()) {
            try {
                assertTrue(gcsLock.isLocked());

                verify(lifecycleListener, after(configuration.getRefreshIntervalInSeconds() * 1000).never())
                    .keepLockAliveException(exceptionArgumentCaptor.capture());
            } finally {
                gcsLock.unlock();
            }
        }

        verify(storage, times(1)).delete((BlobId) any(), (BlobSourceOption) any(), any());
        verify(lifecycleListener, never()).cleanupDeadLockException(any());
        verify(storage, never()).update(any(), any(), (BlobTargetOption) any());
    }

    /**
     * test the scenario when exception is encountered during the lock keep alive process after being successfully acquired.
     *
     * the exception could be due to deletion or recreation of the lock file by some other process when this process failed to keep it alive in a
     * timely fashion
     */
    @Test
    public void testKeepAliveRecreatedLock() {
        when(storage.create(any(), (byte[]) any(), (Storage.BlobTargetOption) any())).thenReturn(blob);
        when(storage.get(anyString(), anyString(), any(), any())).thenThrow(new RuntimeException(LOCK_RETRIEVAL_EXCEPTION));

        GcsLock gcsLock = new GcsLock(configuration);
        gcsLock.addLockListener(lifecycleListener);

        if (gcsLock.tryLock()) {
            try {
                assertTrue(gcsLock.isLocked());

                verify(lifecycleListener, after(configuration.getRefreshIntervalInSeconds() * 1000).times(1))
                    .keepLockAliveException(exceptionArgumentCaptor.capture());
                assertEquals(LOCK_RETRIEVAL_EXCEPTION, exceptionArgumentCaptor.getValue().getMessage());
            } finally {
                gcsLock.unlock();
            }
        }

        verify(storage, times(1)).delete((BlobId) any(), (BlobSourceOption) any(), any());
        verify(lifecycleListener, never()).cleanupDeadLockException(any());
        verify(storage, never()).update(any(), any(), (BlobTargetOption) any());
        verify(storage, times(1)).get(anyString(), anyString(), any(), any());
    }

    /**
     * test the scenario when the lock file is kept alive after being successfully acquired
     */
    @Test
    public void testKeepAliveLongLivingLock() {
        when(storage.create(any(), (byte[]) any(), (Storage.BlobTargetOption) any())).thenReturn(blob);
        when(storage.get(anyString(), anyString(), any(), any())).thenReturn(blob);
        when(storage.update(any(), any(), (BlobTargetOption) any())).thenReturn(blob);

        GcsLock gcsLock = new GcsLock(configuration);
        gcsLock.addLockListener(lifecycleListener);

        if (gcsLock.tryLock()) {
            try {
                assertTrue(gcsLock.isLocked());

                verify(lifecycleListener, after((long) (configuration.getRefreshIntervalInSeconds() * 1000 * 4.5)).never())
                    .keepLockAliveException(exceptionArgumentCaptor.capture());
                verify(storage, atLeast(4)).get(anyString(), anyString(), any(), any());
                verify(storage, atLeast(4)).update(any(), any(), (BlobTargetOption) any());
            } finally {
                gcsLock.unlock();
            }
        }

        verify(storage, times(1)).delete((BlobId) any(), (BlobSourceOption) any(), any());
        verify(lifecycleListener, never()).cleanupDeadLockException(any());
    }


    /**
     * test the scenario when the lock file is gone after a failed acquisition
     */
    @Test
    public void testCleanupGoneLock() {
        when(storage.create(any(), (byte[]) any(), (Storage.BlobTargetOption) any())).thenThrow(storageException);
        when((storage.get(anyString(), anyString()))).thenReturn(null);

        GcsLock gcsLock = new GcsLock(configuration);
        gcsLock.addLockListener(lifecycleListener);

        if (gcsLock.tryLock()) {
            try {
                assertFalse(gcsLock.isLocked());

                verify(lifecycleListener, after((long) (configuration.getRefreshIntervalInSeconds() * 1000 * 1.5)).never())
                    .cleanupDeadLockException(exceptionArgumentCaptor.capture());
            } finally {
                gcsLock.unlock();
            }
        }

        verify(storage, never()).delete((BlobId) any(), (BlobSourceOption) any(), any());
        verify(lifecycleListener, never()).keepLockAliveException(any());
        verify(storage, never()).delete((BlobId) any(), (BlobSourceOption) any(), any());
    }

    /**
     * test the scenario when the lock file is expired after a failed acquisition
     */
    @Test
    public void testCleanupExpiredLock() {
        when(storage.create(any(), (byte[]) any(), (Storage.BlobTargetOption) any())).thenThrow(storageException);
        when((storage.get(anyString(), anyString()))).thenReturn(blob);
        when(metaData.get(LOCK_TIME_TO_LIVE_EPOCH_MS)).thenReturn(String.valueOf(System.currentTimeMillis() - 100));

        GcsLock gcsLock = new GcsLock(configuration);
        gcsLock.addLockListener(lifecycleListener);

        assertFalse(gcsLock.tryLock());

        assertFalse(gcsLock.isLocked());

        verify(lifecycleListener, after((long) (configuration.getRefreshIntervalInSeconds() * 1000 * 1.5)).never())
            .cleanupDeadLockException(exceptionArgumentCaptor.capture());

        verify(lifecycleListener, never()).keepLockAliveException(any());
        verify(storage, never()).get(anyString(), anyString(), any(), any());
        verify(storage, never()).update(any(), any(), (BlobTargetOption) any());
    }

    /**
     * test the scenario an exception is encountered when the expired lock file is being deleted after a failed acquisition
     */
    @Test
    public void testCleanupExpiredLockDeletionException() {
        when(storage.create(any(), (byte[]) any(), (Storage.BlobTargetOption) any())).thenThrow(storageException);
        when((storage.get(anyString(), anyString()))).thenReturn(blob);
        when(metaData.get(LOCK_TIME_TO_LIVE_EPOCH_MS)).thenReturn(String.valueOf(System.currentTimeMillis() - 100));
        when(storage.delete((BlobId) any(), (BlobSourceOption) any(), any())).thenThrow(new RuntimeException(LOCK_DELETION_EXCEPTION));

        GcsLock gcsLock = new GcsLock(configuration);
        gcsLock.addLockListener(lifecycleListener);

        assertFalse(gcsLock.tryLock());

        assertFalse(gcsLock.isLocked());

        verify(lifecycleListener, timeout((long) (configuration.getRefreshIntervalInSeconds() * 1000 * 1.5)).times(1))
            .cleanupDeadLockException(exceptionArgumentCaptor.capture());
        assertEquals(LOCK_DELETION_EXCEPTION, exceptionArgumentCaptor.getValue().getMessage());

        verify(lifecycleListener, never()).keepLockAliveException(any());
        verify(storage, never()).get(anyString(), anyString(), any(), any());
        verify(storage, never()).update(any(), any(), (BlobTargetOption) any());
    }

    /**
     * test the scenario when the lock is long living after a failed acquisition
     */
    @Test
    public void testCleanupLongLivingLock() {
        when(storage.create(any(), (byte[]) any(), (Storage.BlobTargetOption) any())).thenThrow(storageException);
        when((storage.get(anyString(), anyString()))).thenReturn(blob);
        when(metaData.get(LOCK_TIME_TO_LIVE_EPOCH_MS)).thenReturn(String.valueOf(Long.MAX_VALUE));

        GcsLock gcsLock = new GcsLock(configuration);
        gcsLock.addLockListener(lifecycleListener);

        if (gcsLock.tryLock()) {
            try {
                assertFalse(gcsLock.isLocked());

                verify(lifecycleListener, after((long) (configuration.getRefreshIntervalInSeconds() * 1000 * 4.5)).never())
                    .cleanupDeadLockException(exceptionArgumentCaptor.capture());
                verify(blob, atLeast(4)).getMetadata();
            } finally {
                gcsLock.unlock();
            }
        }

        verify(storage, never()).delete((BlobId) any(), (BlobSourceOption) any(), any());
        verify(lifecycleListener, never()).keepLockAliveException(any());
    }

    private void mockObjects() {
        storageOptions = mock(StorageOptions.class);
        storage = mock(Storage.class);
        blob = mock(Blob.class);
        blobBuilder = mock(Blob.Builder.class);
        metaData = mock(Map.class);
    }
}