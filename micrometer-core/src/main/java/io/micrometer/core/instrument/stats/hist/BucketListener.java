package io.micrometer.core.instrument.stats.hist;

public interface BucketListener<T> {
    void bucketAdded(Bucket<T> bucket);
}
