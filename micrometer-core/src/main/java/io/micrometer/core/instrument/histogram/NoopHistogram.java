package io.micrometer.core.instrument.histogram;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.Histogram;

final class NoopHistogram extends Histogram {

    private static final long serialVersionUID = 82886959971723882L;

    static final NoopHistogram INSTANCE = new NoopHistogram();

    private NoopHistogram() {
        super(1, 2, 0);
    }

    @Override
    public Histogram copy() {
        return this;
    }

    @Override
    public Histogram copyCorrectedForCoordinatedOmission(long expectedIntervalBetweenValueSamples) {
        return this;
    }

    @Override
    public long getTotalCount() {
        return 0;
    }

    @Override
    public boolean isAutoResize() {
        return true;
    }

    @Override
    public boolean supportsAutoResize() {
        return true;
    }

    @Override
    public void setAutoResize(boolean autoResize) {}

    @Override
    public void recordValue(long value) {}

    @Override
    public void recordValueWithCount(long value, long count) {}

    @Override
    public void recordValueWithExpectedInterval(long value, long expectedIntervalBetweenValueSamples) {}

    @Override
    public void recordConvertedDoubleValueWithCount(double value, long count) {}

    @Override
    public void recordValue(long value, long expectedIntervalBetweenValueSamples) {}

    @Override
    public void reset() {}

    @Override
    public void copyInto(AbstractHistogram targetHistogram) {}

    @Override
    public void copyIntoCorrectedForCoordinatedOmission(AbstractHistogram targetHistogram,
                                                        long expectedIntervalBetweenValueSamples) {}

    @Override
    public void add(AbstractHistogram otherHistogram) {}

    @Override
    public void subtract(AbstractHistogram otherHistogram) {}

    @Override
    public void addWhileCorrectingForCoordinatedOmission(AbstractHistogram otherHistogram,
                                                         long expectedIntervalBetweenValueSamples) {}

    @Override
    public void shiftValuesLeft(int numberOfBinaryOrdersOfMagnitude) {}

    @Override
    public void shiftValuesRight(int numberOfBinaryOrdersOfMagnitude) {}

    @Override
    public void setStartTimeStamp(long timeStampMsec) {}

    @Override
    public void setEndTimeStamp(long timeStampMsec) {}

    @Override
    public void setTag(String tag) {}
}
