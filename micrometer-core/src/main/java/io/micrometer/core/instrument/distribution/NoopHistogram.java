/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.distribution;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.Histogram;

final class NoopHistogram extends Histogram {

    static final NoopHistogram INSTANCE = new NoopHistogram();
    private static final long serialVersionUID = 82886959971723882L;

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
    public void setAutoResize(boolean autoResize) {
    }

    @Override
    public boolean supportsAutoResize() {
        return true;
    }

    @Override
    public void recordValue(long value) {
    }

    @Override
    public void recordValueWithCount(long value, long count) {
    }

    @Override
    public void recordValueWithExpectedInterval(long value, long expectedIntervalBetweenValueSamples) {
    }

    @Override
    public void recordConvertedDoubleValueWithCount(double value, long count) {
    }

    @SuppressWarnings("deprecation")
    @Override
    public void recordValue(long value, long expectedIntervalBetweenValueSamples) {
    }

    @Override
    public void reset() {
    }

    @Override
    public void copyInto(AbstractHistogram targetHistogram) {
    }

    @Override
    public void copyIntoCorrectedForCoordinatedOmission(AbstractHistogram targetHistogram,
                                                        long expectedIntervalBetweenValueSamples) {
    }

    @Override
    public void add(AbstractHistogram otherHistogram) {
    }

    @Override
    public void subtract(AbstractHistogram otherHistogram) {
    }

    @Override
    public void addWhileCorrectingForCoordinatedOmission(AbstractHistogram otherHistogram,
                                                         long expectedIntervalBetweenValueSamples) {
    }

    @Override
    public void shiftValuesLeft(int numberOfBinaryOrdersOfMagnitude) {
    }

    @Override
    public void shiftValuesRight(int numberOfBinaryOrdersOfMagnitude) {
    }

    @Override
    public void setStartTimeStamp(long timeStampMsec) {
    }

    @Override
    public void setEndTimeStamp(long timeStampMsec) {
    }

    @Override
    public void setTag(String tag) {
    }
}
