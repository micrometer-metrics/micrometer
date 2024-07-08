/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.prometheus;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exemplars.DefaultExemplarSampler;
import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;
import io.prometheus.client.exporter.common.TextFormat;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.micrometer.core.instrument.MockClock.clock;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PrometheusMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Jonatan Ivanov
 */
class PrometheusMeterRegistryTest {

    private CollectorRegistry prometheusRegistry = new CollectorRegistry(true);

    private MockClock clock = new MockClock();

    private PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, prometheusRegistry,
            clock);

    @Test
    void metersWithSameNameAndDifferentTagsContinueSilently() {
        String meterName = "my.counter";
        registry.counter(meterName, "k1", "v1");
        registry.counter(meterName, "k2", "v2");
        registry.counter(meterName, "k3", "v3");
    }

    @Test
    void meterRegistrationFailedListenerCalledOnSameNameDifferentTags() throws InterruptedException {
        CountDownLatch failedLatch = new CountDownLatch(1);
        registry.config().onMeterRegistrationFailed((id, reason) -> failedLatch.countDown());
        registry.counter("my.counter");
        registry.counter("my.counter", "k", "v").increment();

        assertThat(failedLatch.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> registry.throwExceptionOnRegistrationFailure().counter("my.counter", "k1", "v1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith(
                    "Prometheus requires that all meters with the same name have the same set of tag keys.");

        assertThatThrownBy(() -> registry.counter("my.counter", "k2", "v2"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith(
                    "Prometheus requires that all meters with the same name have the same set of tag keys.");
    }

    @Test
    void baseUnitMakesItToScrape() {
        AtomicInteger n = new AtomicInteger();
        Gauge.builder("gauge", n, AtomicInteger::get).tags("a", "b").baseUnit(BaseUnits.BYTES).register(registry);
        assertThat(registry.scrape()).contains("gauge_bytes");
    }

    @DisplayName("percentiles are given as a separate sample with a key of 'quantile'")
    @Test
    void quantiles() {
        Timer.builder("timer").publishPercentiles(0.5).register(registry);
        DistributionSummary.builder("ds").publishPercentiles(0.5).register(registry);

        assertThat(prometheusRegistry.metricFamilySamples()).has(withNameAndQuantile("timer_seconds"));
        assertThat(prometheusRegistry.metricFamilySamples()).has(withNameAndQuantile("ds"));
    }

    @Issue("#27")
    @DisplayName("custom distribution summaries respect varying tags")
    @Test
    void customSummaries() {
        Arrays.asList("v1", "v2").forEach(v -> {
            registry.summary("s", "k", v).record(1.0);
            assertThat(registry.getPrometheusRegistry()
                .getSampleValue("s_count", new String[] { "k" }, new String[] { v }))
                .describedAs("distribution summary s with a tag value of %s", v)
                .isEqualTo(1.0, offset(1e-12));
        });
    }

    @DisplayName("custom meters can be typed")
    @Test
    void typedCustomMeters() {
        Meter
            .builder("name", Meter.Type.COUNTER, Collections.singletonList(new Measurement(() -> 1.0, Statistic.COUNT)))
            .register(registry);

        Collector.MetricFamilySamples metricFamilySamples = registry.getPrometheusRegistry()
            .metricFamilySamples()
            .nextElement();
        assertThat(metricFamilySamples.type).describedAs("custom counter with a type of COUNTER")
            .isEqualTo(Collector.Type.COUNTER);
        assertThat(metricFamilySamples.samples.get(0).labelNames).containsExactly("statistic");
        assertThat(metricFamilySamples.samples.get(0).labelValues).containsExactly("COUNT");
    }

    @DisplayName("attempts to register different meter types with the same name fail somewhat gracefully")
    @Test
    void differentMeterTypesWithSameName() {
        registry.timer("m");
        assertThatIllegalArgumentException().isThrownBy(() -> registry.counter("m"));
    }

    @DisplayName("description text is bound to 'help' on Prometheus collectors")
    @Test
    void helpText() {
        Timer.builder("timer").description("my timer").register(registry);
        Counter.builder("counter").description("my counter").register(registry);
        DistributionSummary.builder("summary").description("my summary").register(registry);
        Gauge.builder("gauge", new AtomicInteger(), AtomicInteger::doubleValue)
            .description("my gauge")
            .register(registry);
        LongTaskTimer.builder("long.task.timer").description("my long task timer").register(registry);

        assertThat(registry.scrape()).contains("HELP timer_seconds my timer")
            .contains("HELP summary my summary")
            .contains("HELP gauge my gauge")
            .contains("HELP counter_total my counter")
            .contains("HELP long_task_timer_seconds my long task timer");
    }

    @Issue("#249")
    @Test
    void type() {
        Timer.builder("t1").register(registry);
        Timer.builder("t2").publishPercentileHistogram().register(registry);

        assertThat(registry.scrape()).contains("# TYPE t1_seconds summary").contains("# TYPE t2_seconds histogram");
    }

    @Test
    void namingConventionOfCustomMeters() {
        CompositeMeterRegistry composite = new CompositeMeterRegistry();
        composite.add(registry);

        registry.more().counter("my.custom", emptyList(), 0);
        assertThat(registry.scrape()).contains("my_custom");
    }

    @Test
    void percentileTimersContainPositiveInfinity() {
        Timer timer = Timer.builder("my.timer").publishPercentileHistogram().register(registry);
        timer.record(1, TimeUnit.MILLISECONDS);

        assertThat(registry.scrape()).contains("le=\"+Inf\"");
    }

    @Test
    void percentileTimersAreClampedByDefault() {
        Timer timer = Timer.builder("my.timer").publishPercentileHistogram().register(registry);
        timer.record(1, TimeUnit.MILLISECONDS);

        assertThat(Arrays.stream(registry.scrape().split("\n")).filter(l -> l.contains("le="))).hasSize(69);
    }

    @Issue("#127")
    @Test
    void percentileHistogramsAccumulateToInfinityEvenWhenClamped() {
        Timer t = Timer.builder("t1").publishPercentileHistogram().register(registry);

        t.record(106, TimeUnit.SECONDS);

        assertThat(registry.scrape()).contains("t1_seconds_bucket{le=\"+Inf\",} 1.0");
    }

    @Issue("#265")
    @Test
    void percentileHistogramsNeverResetForTimers() {
        Timer t = Timer.builder("t1")
            .publishPercentileHistogram()
            .distributionStatisticExpiry(Duration.ofSeconds(60))
            .serviceLevelObjectives(Duration.ofMillis(100))
            .register(registry);

        t.record(100, TimeUnit.MILLISECONDS);
        clock.addSeconds(60);

        assertThat(registry.scrape()).contains("t1_seconds_bucket{le=\"0.1\",} 1.0");
    }

    @Issue("#265")
    @Test
    void percentileHistogramsNeverResetForSummaries() {
        DistributionSummary s = DistributionSummary.builder("s1")
            .publishPercentileHistogram()
            .distributionStatisticExpiry(Duration.ofSeconds(60))
            .serviceLevelObjectives(100.0)
            .register(registry);

        s.record(100);
        clock.addSeconds(60);

        assertThat(registry.scrape()).contains("s1_bucket{le=\"100.0\",} 1.0");
    }

    @Test
    void percentileHistogramWithUpperBoundContainsExactlyOneInf() {

        DistributionSummary s = DistributionSummary.builder("s")
            .publishPercentileHistogram()
            .maximumExpectedValue(3.0)
            .register(registry);

        s.record(100);

        assertThat(registry.scrape()).containsOnlyOnce("s_bucket{le=\"+Inf\",} 1.0");
    }

    @Test
    void percentileHistogramWithoutUpperBoundContainsExactlyOneInf() {

        DistributionSummary s = DistributionSummary.builder("s").publishPercentileHistogram().register(registry);

        s.record(100);

        assertThat(registry.scrape()).containsOnlyOnce("s_bucket{le=\"+Inf\",} 1.0");
    }

    @Issue("#247")
    @Test
    void distributionPercentileBuckets() {
        DistributionSummary ds = DistributionSummary.builder("ds")
            .publishPercentileHistogram()
            .minimumExpectedValue(1.0)
            .maximumExpectedValue(2100.0)
            .register(registry);

        ds.record(30);
        ds.record(9);
        ds.record(62);

        assertThat(registry.scrape()).contains("ds_bucket{le=\"1.0\",}").contains("ds_bucket{le=\"2100.0\",} 3.0");
    }

    @Issue("#127")
    @Test
    void percentileHistogramsWhenValueIsLessThanTheSmallestBucket() {
        DistributionSummary speedIndexRatings = DistributionSummary.builder("speed.index")
            .tags("page", "home")
            .description("Distribution of 'speed index' ratings")
            .publishPercentileHistogram()
            .register(registry);

        speedIndexRatings.record(0);

        assertThat(registry.scrape()).contains("speed_index_bucket{page=\"home\",le=\"+Inf\",} 1.0");
    }

    @Issue("#370")
    @Test
    void serviceLevelObjectivesOnlyNoPercentileHistogram() {
        DistributionSummary.builder("my.summary").serviceLevelObjectives(1.0).register(registry).record(1);
        assertThat(registry.scrape()).contains("my_summary_bucket{le=\"1.0\",} 1.0");

        Timer.builder("my.timer")
            .serviceLevelObjectives(Duration.ofMillis(1))
            .register(registry)
            .record(1, TimeUnit.MILLISECONDS);
        assertThat(registry.scrape()).contains("my_timer_seconds_bucket{le=\"0.001\",} 1.0");
    }

    @Issue("#61")
    @Test
    void timersRecordMax() {
        Timer timer = registry.timer("my.timer");
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(1, TimeUnit.SECONDS);

        assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(1000);
        assertThat(registry.scrape()).contains("my_timer_seconds_max 1.0");

        clock(registry).add(Duration.ofMillis(PrometheusConfig.DEFAULT.step().toMillis() * bufferLength()));
        assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(0);
        assertThat(registry.scrape()).contains("my_timer_seconds_max 0.0");
    }

    private int bufferLength() {
        // noinspection ConstantConditions
        return DistributionStatisticConfig.DEFAULT.getBufferLength();
    }

    @Issue("#61")
    @Test
    void distributionSummariesRecordMax() {
        DistributionSummary summary = registry.summary("my.summary");
        summary.record(10);
        summary.record(1);

        assertThat(summary.max()).isEqualTo(10);
        assertThat(registry.scrape()).contains("my_summary_max 10.0");

        clock(registry).add(PrometheusConfig.DEFAULT.step().toMillis() * bufferLength(), TimeUnit.MILLISECONDS);
        assertThat(summary.max()).isEqualTo(0);

        assertThat(registry.scrape()).contains("my_summary_max 0.0");
    }

    @Issue("#246")
    @Test
    void functionCounterNamingConvention() {
        FunctionCounter.builder("api.requests", 1.0, n -> n).register(registry);

        assertThat(registry.scrape()).contains("api_requests_total 1.0");
    }

    private Condition<Enumeration<Collector.MetricFamilySamples>> withNameAndQuantile(String name) {
        return new Condition<>(m -> {
            while (m.hasMoreElements()) {
                Collector.MetricFamilySamples samples = m.nextElement();
                if (samples.samples.stream().anyMatch(s -> s.name.equals(name) && s.labelNames.contains("quantile"))) {
                    return true;
                }
            }
            return false;
        }, "a meter with name `%s` and tag `%s`", name, "quantile");
    }

    @Issue("#519")
    @Test
    void timersMultipleMetrics() {
        Timer timer = registry.timer("my.timer");
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(20, TimeUnit.SECONDS);

        String scraped = registry.scrape();
        assertThat(scraped).contains("# TYPE my_timer_seconds_max gauge");
        assertThat(scraped).contains("my_timer_seconds_max 20.0");

        assertThat(scraped).contains("# TYPE my_timer_seconds summary");
        assertThat(scraped).contains("my_timer_seconds_count 2.0");
        assertThat(scraped).contains("my_timer_seconds_sum 20.01");
    }

    @Issue("#519")
    @Test
    void distributionSummariesMultipleMetrics() {
        DistributionSummary summary = registry.summary("my.summary");
        summary.record(20);
        summary.record(1);

        String scraped = registry.scrape();
        assertThat(scraped).contains("# TYPE my_summary_max gauge");
        assertThat(scraped).contains("my_summary_max 20.0");

        assertThat(scraped).contains("# TYPE my_summary summary");
        assertThat(scraped).contains("my_summary_count 2.0");
        assertThat(scraped).contains("my_summary_sum 21.0");
    }

    @Issue("#519")
    @Test
    void containsJustOneTypeHelpComment() {
        Timer timer1 = registry.timer("my.timer", "tag", "value1");
        Timer timer2 = registry.timer("my.timer", "tag", "value2");
        timer1.record(10, TimeUnit.MILLISECONDS);
        timer2.record(1, TimeUnit.SECONDS);

        String scraped = registry.scrape();
        assertThat(scraped).containsOnlyOnce("# TYPE my_timer_seconds_max gauge");
        assertThat(scraped).containsOnlyOnce("# HELP my_timer_seconds_max");
        assertThat(scraped).containsOnlyOnce("# TYPE my_timer_seconds summary");
        assertThat(scraped).containsOnlyOnce("# HELP my_timer_seconds ");
    }

    @Issue("#989")
    @Test
    @DisplayName("removed meters correctly handled")
    void meterRemoval() {
        Timer timer = Timer.builder("timer_to_be_removed").publishPercentiles(0.5).register(registry);

        assertThat(prometheusRegistry.metricFamilySamples()).has(withNameAndQuantile("timer_to_be_removed_seconds"));

        registry.remove(timer);

        assertThat(prometheusRegistry.metricFamilySamples())
            .doesNotHave(withNameAndQuantile("timer_to_be_removed_seconds"));
    }

    @Test
    void timerQuantilesAreBasedOffOfOnlyRecentSamples() {
        Timer timer = Timer.builder("my.timer")
            .publishPercentiles(1.0)
            .distributionStatisticBufferLength(2)
            .distributionStatisticExpiry(Duration.ofMinutes(1))
            .register(registry);

        timer.record(1, TimeUnit.SECONDS);
        assertThat(timer.takeSnapshot().percentileValues()[0].value(TimeUnit.SECONDS)).isEqualTo(1.0, offset(0.1));

        timer.record(5, TimeUnit.SECONDS);
        assertThat(timer.takeSnapshot().percentileValues()[0].value(TimeUnit.SECONDS)).isEqualTo(5.0, offset(0.1));

        clock.addSeconds(60);

        timer.record(2, TimeUnit.SECONDS);

        assertThat(timer.takeSnapshot().percentileValues()[0].value(TimeUnit.SECONDS)).isEqualTo(2.0, offset(0.1));
    }

    @Test
    void summaryQuantilesAreBasedOffOfOnlyRecentSamples() {
        DistributionSummary timer = DistributionSummary.builder("my.summary")
            .publishPercentiles(1.0)
            .distributionStatisticBufferLength(2)
            .distributionStatisticExpiry(Duration.ofMinutes(1))
            .register(registry);

        timer.record(1);
        assertThat(timer.takeSnapshot().percentileValues()[0].value()).isEqualTo(1.0, offset(0.2));

        timer.record(5);
        assertThat(timer.takeSnapshot().percentileValues()[0].value()).isEqualTo(5.0, offset(0.2));

        clock.addSeconds(60);

        timer.record(2);

        assertThat(timer.takeSnapshot().percentileValues()[0].value()).isEqualTo(2.0, offset(0.2));
    }

    @Issue("#1883")
    @Test
    void namesToCollectors() {
        AtomicInteger n = new AtomicInteger();
        Gauge.builder("gauge", n, AtomicInteger::get).tags("a", "b").baseUnit(BaseUnits.BYTES).register(registry);
        assertThat(prometheusRegistry).extracting("namesToCollectors").extracting("gauge_bytes").isNotNull();
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithCounter() {
        String[] names = { "my_count_total" };

        Counter.builder("my.count").register(registry);
        assertFilteredMetricFamilySamples(names, names);
    }

    private void assertFilteredMetricFamilySamples(String[] includedNames, String[] expectedNames) {
        Enumeration<Collector.MetricFamilySamples> metricFamilySamples = registry.getPrometheusRegistry()
            .filteredMetricFamilySamples(new HashSet<>(Arrays.asList(includedNames)));
        String[] names = Collections.list(metricFamilySamples)
            .stream()
            .flatMap(metricFamilySample -> metricFamilySample.samples.stream())
            .map(sample -> sample.name)
            .toArray(String[]::new);
        assertThat(names).containsExactlyInAnyOrder(expectedNames);
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithGauge() {
        String[] names = { "my_gauge" };

        Gauge.builder("my.gauge", () -> 1d).register(registry);
        assertFilteredMetricFamilySamples(names, names);
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithTimer() {
        String[] names = { "my_timer_seconds_count", "my_timer_seconds_sum", "my_timer_seconds_max" };

        Timer.builder("my.timer").register(registry);
        assertFilteredMetricFamilySamples(names, names);
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithLongTaskTimer() {
        String[] includedNames = { "my_long_task_timer_seconds", "my_long_task_timer_seconds_max",
                "my_long_task_timer_seconds_active_count", "my_long_task_timer_seconds_duration_sum" };
        String[] expectedNames = { "my_long_task_timer_seconds_max", "my_long_task_timer_seconds_active_count",
                "my_long_task_timer_seconds_duration_sum" };

        LongTaskTimer.builder("my.long.task.timer").register(registry);
        assertFilteredMetricFamilySamples(includedNames, expectedNames);
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithDistributionSummary() {
        String[] names = { "my_distribution_summary_count", "my_distribution_summary_sum",
                "my_distribution_summary_max" };

        DistributionSummary.builder("my.distribution.summary").register(registry);
        assertFilteredMetricFamilySamples(names, names);
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithCustomMeter() {
        String[] includedNames = { "my_custom_meter", "my_custom_meter_sum", "my_custom_meter_max" };
        String[] expectedNames = { "my_custom_meter_sum", "my_custom_meter_max" };

        List<Measurement> measurements = Arrays.asList(new Measurement(() -> 1d, Statistic.TOTAL),
                new Measurement(() -> 1d, Statistic.MAX));
        Meter.builder("my.custom.meter", Meter.Type.OTHER, measurements).register(registry);
        assertFilteredMetricFamilySamples(includedNames, expectedNames);
    }

    @Issue("#2060")
    @Test
    void timerSumAndMaxHaveCorrectBaseUnit_whenPercentileHistogramEnabled() {
        Timer timer = Timer.builder("my.timer").publishPercentileHistogram().register(registry);

        timer.record(1, TimeUnit.SECONDS);
        HistogramSnapshot histogramSnapshot = timer.takeSnapshot();
        assertThat(histogramSnapshot.total(TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(histogramSnapshot.max(TimeUnit.SECONDS)).isEqualTo(1);
        String scrape = registry.scrape();
        assertThat(scrape).contains("my_timer_seconds_sum 1.0\n");
        assertThat(scrape).contains("my_timer_seconds_max 1.0\n");
    }

    @Test
    void scrapeWithLongTaskTimer() {
        LongTaskTimer.builder("my.long.task.timer").register(registry);
        assertThat(registry.scrape()).contains("my_long_task_timer_seconds_max")
            .contains("my_long_task_timer_seconds_active_count")
            .contains("my_long_task_timer_seconds_duration_sum");
    }

    @Issue("#2087")
    @Test
    void meterTriggeringAnotherMeterWhenCollectingValue() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        Gauge.builder("my.gauge", () -> {
            Future<?> future = executorService.submit(() -> {
                Gauge.builder("another.gauge", () -> 2d).register(registry);
            });

            try {
                future.get();
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
            catch (ExecutionException ex) {
                throw new RuntimeException(ex);
            }
            return 1d;
        }).register(registry);

        assertThat(registry.get("my.gauge").gauge().value()).isEqualTo(1d);
        assertThat(registry.get("another.gauge").gauge().value()).isEqualTo(2d);

        executorService.shutdownNow();
    }

    @Test
    void openMetricsScrape() {
        Counter.builder("my.counter").baseUnit("bytes").register(registry);
        Timer.builder("my.timer").register(registry);
        assertThat(registry.scrape(TextFormat.CONTENT_TYPE_OPENMETRICS_100))
            .contains("# TYPE my_counter_bytes counter\n" + "# HELP my_counter_bytes  \n"
                    + "my_counter_bytes_total 0.0\n")
            .contains("# TYPE my_timer_seconds_max gauge\n" + "# HELP my_timer_seconds_max  \n"
                    + "my_timer_seconds_max 0.0\n")
            .contains("# TYPE my_timer_seconds summary\n" + "# HELP my_timer_seconds  \n"
                    + "my_timer_seconds_count 0.0\n" + "my_timer_seconds_sum 0.0\n")
            .endsWith("# EOF\n");
    }

    @Test
    void openMetricsScrapeWithExemplars() {
        DefaultExemplarSampler exemplarSampler = new DefaultExemplarSampler(new TestSpanContextSupplier());
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, prometheusRegistry,
                clock, exemplarSampler);

        Counter counter = Counter.builder("my.counter").register(registry);
        counter.increment();

        Timer timer = Timer.builder("timer.noHistogram").register(registry);
        timer.record(Duration.ofMillis(100));
        timer.record(Duration.ofMillis(200));
        timer.record(Duration.ofMillis(150));

        Timer timerWithHistogram = Timer.builder("timer.withHistogram")
            .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(300))
            .register(registry);
        timerWithHistogram.record(Duration.ofMillis(15));
        timerWithHistogram.record(Duration.ofMillis(1_500));
        timerWithHistogram.record(Duration.ofMillis(150));

        DistributionSummary summary = DistributionSummary.builder("summary.noHistogram").register(registry);
        summary.record(0.10);
        summary.record(1E18);
        summary.record(20);

        DistributionSummary summaryWithHistogram = DistributionSummary.builder("summary.withHistogram")
            .publishPercentileHistogram()
            .register(registry);
        summaryWithHistogram.record(0.15);
        summaryWithHistogram.record(5E18);
        summaryWithHistogram.record(15);

        DistributionSummary slos = DistributionSummary.builder("summary.withSlos")
            .serviceLevelObjectives(100, 200, 300)
            .register(registry);
        slos.record(10);
        slos.record(1_000);
        slos.record(250);

        String scraped = registry.scrape(TextFormat.CONTENT_TYPE_OPENMETRICS_100);
        assertThat(scraped).contains("my_counter_total 1.0 # {span_id=\"1\",trace_id=\"2\"} 1.0 ");

        assertThat(scraped).contains("timer_noHistogram_seconds_count 3.0 # {span_id=\"3\",trace_id=\"4\"} 1.0 ");

        assertThat(scraped)
            .contains("timer_withHistogram_seconds_bucket{le=\"0.1\"} 1.0 # {span_id=\"5\",trace_id=\"6\"} 0.015 ")
            .contains("timer_withHistogram_seconds_bucket{le=\"0.2\"} 2.0 # {span_id=\"9\",trace_id=\"10\"} 0.15 ")
            .contains("timer_withHistogram_seconds_bucket{le=\"0.3\"} 2.0\n")
            .contains("timer_withHistogram_seconds_bucket{le=\"+Inf\"} 3.0 # {span_id=\"7\",trace_id=\"8\"} 1.5 ");
        assertThat(scraped).contains("timer_withHistogram_seconds_count 3.0 # {span_id=\"9\",trace_id=\"10\"} 1.0 ");

        assertThat(scraped).contains("summary_noHistogram_count 3.0 # {span_id=\"11\",trace_id=\"12\"} 1.0 ");

        assertThat(scraped)
            .contains("summary_withHistogram_bucket{le=\"1.0\"} 1.0 # {span_id=\"13\",trace_id=\"14\"} 0.15 ")
            .contains("summary_withHistogram_bucket{le=\"16.0\"} 2.0 # {span_id=\"17\",trace_id=\"18\"} 15.0 ")
            .contains("summary_withHistogram_bucket{le=\"+Inf\"} 3.0 # {span_id=\"15\",trace_id=\"16\"} 5.0E18 ");
        assertThat(scraped).contains("summary_withHistogram_count 3.0 # {span_id=\"17\",trace_id=\"18\"} 1.0 ");

        assertThat(scraped)
            .contains("summary_withSlos_bucket{le=\"100.0\"} 1.0 # {span_id=\"19\",trace_id=\"20\"} 10.0 ")
            .contains("summary_withSlos_bucket{le=\"200.0\"} 1.0\n")
            .contains("summary_withSlos_bucket{le=\"300.0\"} 2.0 # {span_id=\"23\",trace_id=\"24\"} 250.0 ")
            .contains("summary_withSlos_bucket{le=\"+Inf\"} 3.0 # {span_id=\"21\",trace_id=\"22\"} 1000.0 ");
        assertThat(scraped).contains("summary_withSlos_count 3.0 # {span_id=\"23\",trace_id=\"24\"} 1.0 ");

        assertThat(scraped).endsWith("# EOF\n");
    }

    @Test
    void noExemplarsIfNoSampler() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, prometheusRegistry,
                clock);

        Counter counter = Counter.builder("my.counter").register(registry);
        counter.increment();

        Timer timer = Timer.builder("test.timer")
            .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(300))
            .register(registry);
        timer.record(Duration.ofMillis(15));
        timer.record(Duration.ofMillis(150));
        timer.record(Duration.ofMillis(1_500));

        DistributionSummary histogram = DistributionSummary.builder("test.histogram")
            .publishPercentileHistogram()
            .register(registry);
        histogram.record(0.15);
        histogram.record(15);
        histogram.record(5E18);

        DistributionSummary slos = DistributionSummary.builder("test.slos")
            .serviceLevelObjectives(100, 200, 300)
            .register(registry);
        slos.record(10);
        slos.record(250);
        slos.record(1_000);

        String scraped = registry.scrape(TextFormat.CONTENT_TYPE_OPENMETRICS_100);
        assertThat(scraped).contains("my_counter_total 1.0\n");
        assertThat(scraped).contains("test_timer_seconds_bucket{le=\"0.1\"} 1.0\n")
            .contains("test_timer_seconds_bucket{le=\"0.2\"} 2.0\n")
            .contains("test_timer_seconds_bucket{le=\"0.3\"} 2.0\n")
            .contains("test_timer_seconds_bucket{le=\"+Inf\"} 3.0\n");
        assertThat(scraped).contains("test_histogram_bucket{le=\"1.0\"} 1.0\n")
            .contains("test_histogram_bucket{le=\"16.0\"} 2.0\n")
            .contains("test_histogram_bucket{le=\"+Inf\"} 3.0\n");
        assertThat(scraped).contains("test_slos_bucket{le=\"100.0\"} 1.0\n")
            .contains("test_slos_bucket{le=\"200.0\"} 1.0\n")
            .contains("test_slos_bucket{le=\"300.0\"} 2.0\n")
            .contains("test_slos_bucket{le=\"+Inf\"} 3.0\n");
        assertThat(scraped).doesNotContain("span_id").doesNotContain("trace_id");
        assertThat(scraped).endsWith("# EOF\n");
    }

    @Test
    @Issue("#5229")
    void doesNotCallConventionOnScrape() {
        CountingPrometheusNamingConvention convention = new CountingPrometheusNamingConvention();
        registry.config().namingConvention(convention);

        Timer.builder("timer").tag("k1", "v1").description("my timer").register(registry);
        Counter.builder("counter").tag("k1", "v1").description("my counter").register(registry);
        DistributionSummary.builder("summary").tag("k1", "v1").description("my summary").register(registry);
        Gauge.builder("gauge", new AtomicInteger(), AtomicInteger::doubleValue)
            .tag("k1", "v1")
            .description("my gauge")
            .register(registry);
        LongTaskTimer.builder("long.task.timer").tag("k1", "v1").description("my long task timer").register(registry);

        int expectedNameCount = convention.nameCount.get();
        int expectedTagKeyCount = convention.tagKeyCount.get();

        registry.scrape();

        assertThat(convention.nameCount.get()).isEqualTo(expectedNameCount);
        assertThat(convention.tagKeyCount.get()).isEqualTo(expectedTagKeyCount);
    }

    private static class CountingPrometheusNamingConvention extends PrometheusNamingConvention {

        AtomicInteger nameCount = new AtomicInteger();

        AtomicInteger tagKeyCount = new AtomicInteger();

        @Override
        public String name(String name, Meter.Type type, @Nullable String baseUnit) {
            nameCount.incrementAndGet();
            return super.name(name, type, baseUnit);
        }

        @Override
        public String tagKey(String key) {
            tagKeyCount.incrementAndGet();
            return super.tagKey(key);
        }

    }

    static class TestSpanContextSupplier implements SpanContextSupplier {

        private final AtomicLong count = new AtomicLong();

        @Override
        public String getTraceId() {
            return String.valueOf(count.incrementAndGet());
        }

        @Override
        public String getSpanId() {
            return String.valueOf(count.incrementAndGet());
        }

        @Override
        public boolean isSampled() {
            return true;
        }

    }

}
