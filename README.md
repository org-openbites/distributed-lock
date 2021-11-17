# distributed-lock

The `DistributedLock` interface is designed to provide an easy-to-use, easy-to-maintain, and cost-effective synchronization primitive to a set of globally distributed processes that run concurrently.  

The `GcsLock` implementation is based on the strong consistency of Google Cloud Storage that offers the essential `Compare And Set` (CAS) semantics. Once obtained, the `GcsLock` object would extend its lifetime periodically based on the configuration provided while the lock object is constructed. The processes that couldn't obtain the lock would run a cleanup logic in case the lock object is expired and could not be unlocked properly by the lock owner.

# Production Ready Features
* Automatically keep the lock alive until unlock
* Automatically cleanup a deadlock in case of system failure
* Listener for callback of erroneous situation 
* Hardened against inter-process race conditions
* Thread safe and reentrant
* java.util.concurrent.locks.Lock interface and semantics compliant

## Usage

### distributed-lock-core
```
<dependency>
  <groupId>org.openbites</groupId>
  <artifactId>distributed-lock-core</artifactId>
  <version>2.0.0</version>
</dependency>

GcsLockConfig configuration = GcsLockConfig.newBuilder().setGcsBucketName("org-openbites-distributed-lock")
                                     .setGcsLockFilename("test-distributed-lock")
                                     .setRefreshIntervalInSeconds(10)
                                     .setLifeExtensionInSeconds(60)
                                     .build();
GcsLock gcsLock = new GcsLock(configuration);

GcsLockListener lockListener = ...;    // optional
gcsLock.addLockListener(lockListener); // optional

if (gcsLock.tryLock()) {
  try {
    // critical setion that is meant to be executed once by a set of globally distributed processes that run concurrently
  } finally {
     gcsLock.unlock();
  }                                     
}
```
### distributed-lock-integration
#### spring-integration
```
<dependency>
  <groupId>org.openbites</groupId>
  <artifactId>distributed-lock-integration</artifactId>
  <version>2.0.0</version>
</dependency>

GcsLockRegistry gcsLockRegistry = new GcsLockRegistry();
GcsLockConfig configuration = GcsLockConfig.newBuilder().setGcsBucketName("org-openbites-distributed-lock")
                                     .setGcsLockFilename("test-distributed-lock")
                                     .setRefreshIntervalInSeconds(10)
                                     .setLifeExtensionInSeconds(60)
                                     .build();
Lock gcsLock = gcsLockRegistry.obtain(configuration);

GcsLockListener lockListener = ...;    // optional
((GcsLock) gcsLock).addLockListener(lockListener); // optional

if (gcsLock.tryLock()) {
  try {
    // critical setion that is meant to be executed once by a set of globally distributed processes that run concurrently
  } finally {
     gcsLock.unlock();
  }                                     
}
```
## Notes
* Due to the rate limitation as well as latency of invoking the Google GCS API the `GcsLock` object is not meant to be used in a High Throughput High Available applications.
* The `GcsLock` object is thread-safe.  However, it is not recommended being used as the inter-thread synchronization primitive in the same JVM.
* Cost estimate.  For a hypothetical usage that could be well over any typical usage pattern where a lock is obtained every 90 seconds and keeping it alive every 10 seconds with 50 other concurrent processes , the monthly cost is estimated to be below $9 based on the November 2021 pricing.   

## References:
* GCS Strong Consistency: https://cloud.google.com/storage/docs/consistency
* GCS Rate Limit: https://cloud.google.com/storage/docs/request-rate
* Cost Estimator: https://cloud.google.com/products/calculator

