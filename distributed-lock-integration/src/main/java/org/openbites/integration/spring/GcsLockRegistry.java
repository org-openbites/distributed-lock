package org.openbites.integration.spring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

import org.openbites.concurrent.locks.gcs.GcsLock;
import org.openbites.concurrent.locks.gcs.GcsLockConfig;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.util.Assert;

/**
 * A {@link LockRegistry} using a Google Storage Bucket to co-ordinate the locks, where the locks taken will be global.
 *
 * @author Williams Simon (openbites.org@gmail.com)
 */
public class GcsLockRegistry implements LockRegistry {

	private final Map<GcsLockConfig, GcsLock> locks = new ConcurrentHashMap<>();

	@Override
	public Lock obtain(Object lockKey) {
		Assert.notNull(lockKey, "GcsConfig is null");
		Assert.isInstanceOf(GcsLockConfig.class, lockKey);
		return this.locks.computeIfAbsent((GcsLockConfig) lockKey, (key) -> new GcsLock(key));
	}
}
