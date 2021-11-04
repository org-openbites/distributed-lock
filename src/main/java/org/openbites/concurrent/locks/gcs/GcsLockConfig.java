package org.openbites.concurrent.locks.gcs;

public class GcsLockConfig {

    private String  gcsBucketName;
    private String  gcsLockFilename;
    private Integer refreshIntervalInSeconds;
    private Integer timeToLiveInSeconds;

    private GcsLockConfig() {
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * @return The GCS bucket name where the lock object is stored
     */
    public String getGcsBucketName() {
        return gcsBucketName;
    }

    /**
     * @return The name of lock object in the GCS bucket returned by getGcsBucketName()
     */
    public String getGcsLockFilename() {
        return gcsLockFilename;
    }

    /**
     * @return The interval in seconds when the lock is refreshed by the lock owner or a cleanup is attempted by the other processes that couldn't obtain the lock.
     */
    public Integer getRefreshIntervalInSeconds() {
        return refreshIntervalInSeconds;
    }

    /**
     * @return The length in seconds the lock's expiration is extended by the lock owner.
     */
    public Integer getTimeToLiveInSeconds() {
        return timeToLiveInSeconds;
    }

    @Override
    public String toString() {
        return String.format("[gcsBucketName=%s, gcsLockFilename=%s]", gcsBucketName, gcsLockFilename);
    }

    static final class Builder {

        private String  gcsBucketName;
        private String  gcsLockFilename;
        private Integer refreshIntervalInSeconds;
        private Integer timeToLiveInSeconds;

        private Builder() {
        }

        public GcsLockConfig.Builder setGcsBucketName(String gcsBucketName) {
            if (gcsBucketName == null) {
                throw new NullPointerException("Null gcsBucketName");
            }
            this.gcsBucketName = gcsBucketName;
            return this;
        }

        public GcsLockConfig.Builder setGcsLockFilename(String gcsLockFilename) {
            if (gcsLockFilename == null) {
                throw new NullPointerException("Null gcsLockFilename");
            }
            this.gcsLockFilename = gcsLockFilename;
            return this;
        }

        public GcsLockConfig.Builder setRefreshIntervalInSeconds(Integer refreshIntervalInSeconds) {
            if (refreshIntervalInSeconds == null) {
                throw new NullPointerException("Null refreshIntervalInSeconds");
            }
            this.refreshIntervalInSeconds = refreshIntervalInSeconds;
            return this;
        }

        public GcsLockConfig.Builder setTimeToLiveInSeconds(Integer timeToLiveInSeconds) {
            if (timeToLiveInSeconds == null) {
                throw new NullPointerException("Null timeToLiveInSeconds");
            }
            this.timeToLiveInSeconds = timeToLiveInSeconds;
            return this;
        }

        public GcsLockConfig build() {
            if (this.gcsBucketName == null
                || this.gcsLockFilename == null
                || this.refreshIntervalInSeconds == null
                || this.timeToLiveInSeconds == null) {
                StringBuilder missing = new StringBuilder();
                if (this.gcsBucketName == null) {
                    missing.append(" gcsBucketName");
                }
                if (this.gcsLockFilename == null) {
                    missing.append(" gcsLockFilename");
                }
                if (this.refreshIntervalInSeconds == null) {
                    missing.append(" refreshIntervalInSeconds");
                }
                if (this.timeToLiveInSeconds == null) {
                    missing.append(" timeToLiveInSeconds");
                }
                throw new IllegalStateException("Missing required properties:" + missing);
            }

            GcsLockConfig configuration = new GcsLockConfig();

            configuration.gcsBucketName = this.gcsBucketName;
            configuration.gcsLockFilename = this.gcsLockFilename;
            configuration.refreshIntervalInSeconds = this.refreshIntervalInSeconds;
            configuration.timeToLiveInSeconds = this.timeToLiveInSeconds;

            return configuration;
        }
    }
}
