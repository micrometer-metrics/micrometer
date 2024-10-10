package io.micrometer.registry.otlp;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

class ExemplarCollectorFactory {
    private final Clock clock;
    private final SpanContextProvider spanContextProvider;

    ExemplarCollectorFactory(Clock clock, SpanContextProvider spanContextProvider) {
        this.clock = clock;
        this.spanContextProvider = spanContextProvider;
    }

    ExemplarCollector fixedSize(int size) {
        return new FixedSizeExemplarCollector(clock, spanContextProvider, new StaticCellSelector(0), size);
    }

    @Nullable
    ExemplarCollector forHistogram(DistributionStatisticConfig distributionStatisticConfig, OtlpConfig otlpConfig) {
        // This logic should match the logic from OtlpMeterRegistry#getHistogram(...)
        if (distributionStatisticConfig.isPublishingHistogram()) {
            if (HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM == OtlpMeterRegistry.histogramFlavor(otlpConfig.histogramFlavor(), distributionStatisticConfig)) {
                return null;
            }

            double[] sloBuckets = OtlpMeterRegistry.getSloWithPositiveInf(distributionStatisticConfig);
            return new FixedSizeExemplarCollector(clock, spanContextProvider, new HistogramCellSelector(sloBuckets), sloBuckets.length);
        }

        // Collecting exemplars for percentile histograms is not yet supported
        return null;
    }

    private static class HistogramCellSelector implements FixedSizeExemplarCollector.CellSelector {
        private final double[] boundaries;

        private HistogramCellSelector(double[] boundaries) {
            this.boundaries = boundaries;
        }

        @Override
        public int cellIndexFor(double value) {
            for (int i = 0; i < boundaries.length; ++i) {
                if (value <= boundaries[i]) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public void reset() {
        }
    }

    private static class StaticCellSelector implements FixedSizeExemplarCollector.CellSelector {
        private final int index;

        private StaticCellSelector(int index) {
            this.index = index;
        }

        @Override
        public int cellIndexFor(double value) {
            return index;
        }

        @Override
        public void reset() {
        }
    }
}
