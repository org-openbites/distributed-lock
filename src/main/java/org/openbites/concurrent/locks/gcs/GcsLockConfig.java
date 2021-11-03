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

    public String getGcsBucketName() {
        return gcsBucketName;
    }

    public String getGcsLockFilename() {
        return gcsLockFilename;
    }

    public Integer getRefreshIntervalInSeconds() {
        return refreshIntervalInSeconds;
    }

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
