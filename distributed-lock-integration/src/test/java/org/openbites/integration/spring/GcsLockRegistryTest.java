package org.openbites.integration.spring;

import java.util.Map;
import java.util.concurrent.locks.Lock;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobSourceOption;
import com.google.cloud.storage.Storage.BlobTargetOption;
import com.google.cloud.storage.StorageOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.openbites.concurrent.locks.gcs.GcsLock;
import org.openbites.concurrent.locks.gcs.GcsLockConfig;
import org.openbites.concurrent.locks.gcs.GcsLockListener;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GcsLockRegistryTest {

	private StorageOptions storageOptions;
	private Storage storage;
	private Blob blob;
	private Blob.Builder blobBuilder;
	private Map<String, String> metaData;

	private GcsLockListener lifecycleListener;
	private ArgumentCaptor<Exception> exceptionArgumentCaptor;
	private GcsLockConfig configuration;

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
				.setLifeExtensionInSeconds(Integer.valueOf(60))
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
	public void testObtain() {
		when(storage.create(any(), (byte[]) any(), (Storage.BlobTargetOption) any())).thenReturn(blob);
		when(storage.get(anyString(), anyString(), any(), any())).thenReturn(null);

		GcsLockRegistry gcsLockRegistry = new GcsLockRegistry();

		Lock gcsLock = gcsLockRegistry.obtain(configuration);
		((GcsLock) gcsLock).addLockListener(lifecycleListener);

		if (gcsLock.tryLock()) {
			try {
				assertTrue(((GcsLock)gcsLock).isLocked());

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

	private void mockObjects() {
		storageOptions = mock(StorageOptions.class);
		storage = mock(Storage.class);
		blob = mock(Blob.class);
		blobBuilder = mock(Blob.Builder.class);
		metaData = mock(Map.class);
	}
}