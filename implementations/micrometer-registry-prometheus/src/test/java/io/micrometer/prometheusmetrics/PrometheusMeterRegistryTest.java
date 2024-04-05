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
package io.micrometer.prometheusmetrics;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.*;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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

    private PrometheusRegistry prometheusRegistry = new PrometheusRegistry();

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

        assertThat(prometheusRegistry.scrape()).has(withNameAndQuantile("timer_seconds"));
        assertThat(prometheusRegistry.scrape()).has(withNameAndQuantile("ds"));
    }

    @Test
    void invalidMeterNameSuffixesShouldBeRemovedForMetadata() {
        new JvmInfoMetrics().bindTo(registry);
        Gauge.builder("test1.info", () -> 1).register(registry);
        Counter.builder("test2.total").register(registry).increment(2);
        Gauge.builder("test3.created", () -> 3).register(registry);
        Counter.builder("test4.bucket").register(registry).increment(4);

        Counter.builder("test5.info").register(registry).increment(5);
        Gauge.builder("test6.total", () -> 6).register(registry);

        String scrapeResult = registry.scrape();

        assertThat(scrapeResult).contains("# HELP test1_info")
            .contains("# TYPE test1_info gauge")
            .contains("test1_info 1");
        assertThat(scrapeResult).contains("# HELP test2_total")
            .contains("# TYPE test2_total counter")
            .contains("test2_total 2.0");
        assertThat(scrapeResult).contains("# HELP jvm_info").contains("# TYPE jvm_info gauge").contains("jvm_info{");
        assertThat(scrapeResult).contains("# HELP test3").contains("# TYPE test3 gauge").contains("test3 3");
        assertThat(scrapeResult).contains("# HELP test4_total")
            .contains("# TYPE test4_total counter")
            .contains("test4_total 4.0");

        assertThat(scrapeResult).contains("# HELP test5_total")
            .contains("# TYPE test5_total counter")
            .contains("test5_total 5.0");
        assertThat(scrapeResult).contains("# HELP test6").contains("# TYPE test6 gauge").contains("test6 6");
    }

    @Issue("#27")
    @DisplayName("custom distribution summaries respect varying tags")
    @Test
    void customSummaries() {
        Arrays.asList("v1", "v2").forEach(v -> {
            registry.summary("ds", "k", v).record(1.0);
            assertThat(registry.getPrometheusRegistry()
                .scrape(name -> name.equals("ds"))
                .stream()
                .flatMap(snapshot -> snapshot.getDataPoints().stream())
                .filter(dataPoint -> dataPoint.getLabels()
                    .stream()
                    .anyMatch(label -> label.compareTo(new Label("k", v)) == 0))
                .mapToDouble(dataPoint -> ((SummarySnapshot.SummaryDataPointSnapshot) dataPoint).getSum())
                .sum()).describedAs("distribution summary ds with a tag value of %s", v).isEqualTo(1.0, offset(1e-12));
        });
    }

    @DisplayName("custom meters can be typed")
    @Test
    void typedCustomMeters() {
        Meter
            .builder("name", Meter.Type.COUNTER, Collections.singletonList(new Measurement(() -> 1.0, Statistic.COUNT)))
            .register(registry);

        MetricSnapshot snapshot = registry.getPrometheusRegistry().scrape().get(0);
        assertThat(snapshot).describedAs("custom counter with a type of COUNTER").isInstanceOf(CounterSnapshot.class);
        assertThat(
                snapshot.getDataPoints().get(0).getLabels().stream().map(Label::getName).collect(Collectors.toList()))
            .containsExactly("statistic");
        assertThat(
                snapshot.getDataPoints().get(0).getLabels().stream().map(Label::getValue).collect(Collectors.toList()))
            .containsExactly("COUNT");
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

        assertThat(registry.scrape()).contains("t1_seconds_bucket{le=\"+Inf\"} 1");
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

        assertThat(registry.scrape()).contains("t1_seconds_bucket{le=\"0.1\"} 1");
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

        assertThat(registry.scrape()).contains("s1_bucket{le=\"100.0\"} 1");
    }

    @Test
    void percentileHistogramWithUpperBoundContainsExactlyOneInf() {
        // single character names no longer valid
        DistributionSummary s = DistributionSummary.builder("ds")
            .publishPercentileHistogram()
            .maximumExpectedValue(3.0)
            .register(registry);

        s.record(100);

        assertThat(registry.scrape()).containsOnlyOnce("s_bucket{le=\"+Inf\"} 1");
    }

    @Test
    void percentileHistogramWithoutUpperBoundContainsExactlyOneInf() {

        DistributionSummary s = DistributionSummary.builder("ds").publishPercentileHistogram().register(registry);

        s.record(100);

        assertThat(registry.scrape()).containsOnlyOnce("s_bucket{le=\"+Inf\"} 1");
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

        assertThat(registry.scrape()).contains("ds_bucket{le=\"1.0\"} 0").contains("ds_bucket{le=\"2100.0\"} 3");
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

        assertThat(registry.scrape()).contains("speed_index_bucket{page=\"home\",le=\"+Inf\"} 1");
    }

    @Issue("#370")
    @Test
    void serviceLevelObjectivesOnlyNoPercentileHistogram() {
        DistributionSummary.builder("my.summary").serviceLevelObjectives(1.0).register(registry).record(1);
        assertThat(registry.scrape()).contains("my_summary_bucket{le=\"1.0\"} 1");

        Timer.builder("my.timer")
            .serviceLevelObjectives(Duration.ofMillis(1))
            .register(registry)
            .record(1, TimeUnit.MILLISECONDS);
        assertThat(registry.scrape()).contains("my_timer_seconds_bucket{le=\"0.001\"} 1");
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

    private Condition<? super Iterable<? extends MetricSnapshot>> withNameAndQuantile(String name) {
        return new Condition<>(
                metricSnapshots -> ((MetricSnapshots) metricSnapshots).stream()
                    .filter(snapshot -> snapshot.getMetadata().getPrometheusName().equals(name))
                    .flatMap(snapshot -> snapshot.getDataPoints().stream())
                    .filter(SummarySnapshot.SummaryDataPointSnapshot.class::isInstance)
                    .map(SummarySnapshot.SummaryDataPointSnapshot.class::cast)
                    .anyMatch(summaryDataPoint -> summaryDataPoint.getQuantiles().size() > 0),
                "a summary with name `%s` and at least one quantile", name);
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
        assertThat(scraped).contains("my_timer_seconds_count 2");
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
        assertThat(scraped).contains("my_summary_count 2");
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

        assertThat(prometheusRegistry.scrape()).has(withNameAndQuantile("timer_to_be_removed_seconds"));

        registry.remove(timer);

        assertThat(prometheusRegistry.scrape()).doesNotHave(withNameAndQuantile("timer_to_be_removed_seconds"));
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
        assertThat(prometheusRegistry.scrape(name -> name.equals("gauge_bytes"))).isNotEmpty();
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithCounter() {
        // fails with my_count_total since Prometheus client 1.x
        String[] names = { "my_count" };

        Counter.builder("my.count").register(registry);
        assertFilteredMetricSnapshots(names, names);
    }

    private void assertFilteredMetricSnapshots(String[] includedNames, String[] expectedNames) {
        MetricSnapshots snapshots = registry.getPrometheusRegistry()
            .scrape(name -> new HashSet<>(Arrays.asList(includedNames)).contains(name));
        String[] names = snapshots.stream()
            .map(snapshot -> snapshot.getMetadata().getPrometheusName())
            .toArray(String[]::new);
        assertThat(names).containsExactlyInAnyOrder(expectedNames);
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithGauge() {
        String[] names = { "my_gauge" };

        Gauge.builder("my.gauge", () -> 1d).register(registry);
        assertFilteredMetricSnapshots(names, names);
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithTimer() {
        // not individual time series name
        String[] names = { "my_timer_seconds", "my_timer_seconds_max" };

        Timer.builder("my.timer").register(registry);
        assertFilteredMetricSnapshots(names, names);
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithLongTaskTimer() {
        String[] includedNames = { "my_long_task_timer_seconds", "my_long_task_timer_seconds_max" };
        String[] expectedNames = { "my_long_task_timer_seconds_max", "my_long_task_timer_seconds" };

        LongTaskTimer.builder("my.long.task.timer").register(registry);
        assertFilteredMetricSnapshots(includedNames, expectedNames);
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithDistributionSummary() {
        String[] names = { "my_distribution_summary", "my_distribution_summary_max" };

        DistributionSummary.builder("my.distribution.summary").register(registry);
        assertFilteredMetricSnapshots(names, names);
    }

    @Issue("#1883")
    @Test
    void filteredMetricFamilySamplesWithCustomMeter() {
        String[] includedNames = { "my_custom_meter", "my_custom_meter_sum", "my_custom_meter_max" };
        String[] expectedNames = { "my_custom_meter_sum", "my_custom_meter_max" };

        List<Measurement> measurements = Arrays.asList(new Measurement(() -> 1d, Statistic.TOTAL),
                new Measurement(() -> 1d, Statistic.MAX));
        Meter.builder("my.custom.meter", Meter.Type.OTHER, measurements).register(registry);
        assertFilteredMetricSnapshots(includedNames, expectedNames);
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
            // since Prometheus client 1.x, suffix _active_count => _count
            .contains("my_long_task_timer_seconds_count")
            .contains("my_long_task_timer_seconds_sum");
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
        assertThat(registry.scrape("application/openmetrics-text; version=1.0.0; charset=utf-8"))
            .contains("# TYPE my_counter_bytes counter\n" + "# UNIT my_counter_bytes bytes\n"
                    + "# HELP my_counter_bytes  \n" + "my_counter_bytes_total 0.0\n")
            .contains("# TYPE my_timer_seconds_max gauge\n" + "# UNIT my_timer_seconds_max seconds\n"
                    + "# HELP my_timer_seconds_max  \n" + "my_timer_seconds_max 0.0\n")
            .contains("# TYPE my_timer_seconds summary\n" + "# UNIT my_timer_seconds seconds\n"
                    + "# HELP my_timer_seconds  \n" + "my_timer_seconds_count 0\n" + "my_timer_seconds_sum 0.0\n")
            .endsWith("# EOF\n");
    }

    @Test
    void openMetricsScrapeWithExemplars() throws InterruptedException {
        PrometheusConfig prometheusConfig = new PrometheusConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Properties prometheusProperties() {
                Properties properties = new Properties();
                properties.putAll(PrometheusConfig.super.prometheusProperties());
                properties.setProperty("io.prometheus.exemplars.sampleIntervalMilliseconds", "1");
                return properties;
            }
        };

        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(prometheusConfig, prometheusRegistry, clock,
                new TestSpanContex());

        Counter counter = Counter.builder("my.counter").register(registry);
        counter.increment();

        Timer timer = Timer.builder("timer.noHistogram").register(registry);
        timer.record(Duration.ofMillis(100));
        timer.record(Duration.ofMillis(200));
        timer.record(Duration.ofMillis(150));

        Timer timerWithHistogram = Timer.builder("timer.withHistogram").publishPercentileHistogram().register(registry);
        timerWithHistogram.record(15, TimeUnit.MILLISECONDS);
        Thread.sleep(5); // sleeping 5ms since the sample interval limit is 1ms
        timerWithHistogram.record(150, TimeUnit.MILLISECONDS);
        Thread.sleep(5); // sleeping 5ms since the sample interval limit is 1ms
        timerWithHistogram.record(60, TimeUnit.SECONDS);

        Timer timerWithSlos = Timer.builder("timer.withSlos")
            .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(300))
            .register(registry);
        timerWithSlos.record(Duration.ofMillis(15));
        Thread.sleep(5); // sleeping 5ms since the sample interval limit is 1ms
        timerWithSlos.record(Duration.ofMillis(1_500));
        Thread.sleep(5); // sleeping 5ms since the sample interval limit is 1ms
        timerWithSlos.record(Duration.ofMillis(150));

        DistributionSummary summary = DistributionSummary.builder("summary.noHistogram").register(registry);
        summary.record(0.10);
        summary.record(1E18);
        summary.record(20);

        DistributionSummary summaryWithHistogram = DistributionSummary.builder("summary.withHistogram")
            .publishPercentileHistogram()
            .register(registry);
        summaryWithHistogram.record(0.15);
        Thread.sleep(5); // sleeping 5ms since the sample interval limit is 1ms
        summaryWithHistogram.record(5E18);
        Thread.sleep(5); // sleeping 5ms since the sample interval limit is 1ms
        summaryWithHistogram.record(15);

        DistributionSummary slos = DistributionSummary.builder("summary.withSlos")
            .serviceLevelObjectives(100, 200, 300)
            .register(registry);
        slos.record(10);
        Thread.sleep(5); // sleeping 5ms since the sample interval limit is 1ms
        slos.record(1_000);
        Thread.sleep(5); // sleeping 5ms since the sample interval limit is 1ms
        slos.record(250);

        String scraped = registry.scrape("application/openmetrics-text");
        assertThat(scraped).contains("my_counter_total 1.0 # {span_id=\"1\",trace_id=\"2\"} 1.0 ");

        assertThat(scraped).contains("timer_noHistogram_seconds_count 3 # {span_id=\"3\",trace_id=\"4\"} 0.1 ");

        assertThat(scraped)
            .contains(
                    "timer_withHistogram_seconds_bucket{le=\"0.015379112\"} 1 # {span_id=\"5\",trace_id=\"6\"} 0.015 ")
            .contains("timer_withHistogram_seconds_bucket{le=\"0.156587348\"} 2 # {span_id=\"7\",trace_id=\"8\"} 0.15 ")
            .contains("timer_withHistogram_seconds_bucket{le=\"+Inf\"} 3 # {span_id=\"9\",trace_id=\"10\"} 60.0 ");
        assertThat(scraped).contains("timer_withHistogram_seconds_count 3 # {span_id=\"9\",trace_id=\"10\"} 60.0 ");

        assertThat(scraped)
            .contains("timer_withSlos_seconds_bucket{le=\"0.1\"} 1 # {span_id=\"11\",trace_id=\"12\"} 0.015 ")
            .contains("timer_withSlos_seconds_bucket{le=\"0.2\"} 2 # {span_id=\"15\",trace_id=\"16\"} 0.15 ")
            .contains("timer_withSlos_seconds_bucket{le=\"0.3\"} 2\n")
            .contains("timer_withSlos_seconds_bucket{le=\"+Inf\"} 3 # {span_id=\"13\",trace_id=\"14\"} 1.5 ");
        assertThat(scraped).contains("timer_withSlos_seconds_count 3 # {span_id=\"15\",trace_id=\"16\"} 0.15 ");

        assertThat(scraped).contains("summary_noHistogram_count 3 # {span_id=\"17\",trace_id=\"18\"} 0.1 ");

        assertThat(scraped)
            .contains("summary_withHistogram_bucket{le=\"1.0\"} 1 # {span_id=\"19\",trace_id=\"20\"} 0.15 ")
            .contains("summary_withHistogram_bucket{le=\"16.0\"} 2 # {span_id=\"23\",trace_id=\"24\"} 15.0 ")
            .contains("summary_withHistogram_bucket{le=\"+Inf\"} 3 # {span_id=\"21\",trace_id=\"22\"} 5.0E18 ");
        assertThat(scraped).contains("summary_withHistogram_count 3 # {span_id=\"23\",trace_id=\"24\"} 15.0");

        assertThat(scraped).contains("summary_withSlos_bucket{le=\"100.0\"} 1 # {span_id=\"25\",trace_id=\"26\"} 10.0 ")
            .contains("summary_withSlos_bucket{le=\"200.0\"} 1\n")
            .contains("summary_withSlos_bucket{le=\"300.0\"} 2 # {span_id=\"29\",trace_id=\"30\"} 250.0 ")
            .contains("summary_withSlos_bucket{le=\"+Inf\"} 3 # {span_id=\"27\",trace_id=\"28\"} 1000.0 ");
        assertThat(scraped).contains("summary_withSlos_count 3 # {span_id=\"29\",trace_id=\"30\"} 250.0 ");

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

        String scraped = registry.scrape("application/openmetrics-text; version=1.0.0; charset=utf-8");
        assertThat(scraped).contains("my_counter_total 1.0\n");
        assertThat(scraped).contains("test_timer_seconds_bucket{le=\"0.1\"} 1\n")
            .contains("test_timer_seconds_bucket{le=\"0.2\"} 2\n")
            .contains("test_timer_seconds_bucket{le=\"0.3\"} 2\n")
            .contains("test_timer_seconds_bucket{le=\"+Inf\"} 3\n");
        assertThat(scraped).contains("test_histogram_bucket{le=\"1.0\"} 1\n")
            .contains("test_histogram_bucket{le=\"16.0\"} 2\n")
            .contains("test_histogram_bucket{le=\"+Inf\"} 3\n");
        assertThat(scraped).contains("test_slos_bucket{le=\"100.0\"} 1\n")
            .contains("test_slos_bucket{le=\"200.0\"} 1\n")
            .contains("test_slos_bucket{le=\"300.0\"} 2\n")
            .contains("test_slos_bucket{le=\"+Inf\"} 3\n");
        assertThat(scraped).doesNotContain("span_id").doesNotContain("trace_id");
        assertThat(scraped).endsWith("# EOF\n");
    }

    static class TestSpanContex implements SpanContext {

        private final AtomicLong count = new AtomicLong();

        @Override
        public String getCurrentTraceId() {
            return String.valueOf(count.incrementAndGet());
        }

        @Override
        public String getCurrentSpanId() {
            return String.valueOf(count.incrementAndGet());
        }

        @Override
        public boolean isCurrentSpanSampled() {
            return true;
        }

        @Override
        public void markCurrentSpanAsExemplar() {
        }

    }

}
