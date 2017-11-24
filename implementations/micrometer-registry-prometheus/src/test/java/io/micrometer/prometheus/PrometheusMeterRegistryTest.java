/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.prometheus;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micrometer.core.instrument.MockClock.clock;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Jon Schneider
 */
class PrometheusMeterRegistryTest {
    private PrometheusMeterRegistry registry;
    private CollectorRegistry prometheusRegistry;

    @BeforeEach
    void before() {
        prometheusRegistry = new CollectorRegistry();
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, prometheusRegistry, new MockClock());
    }

    @Test
    void baseUnitMakesItToScrape() {
        AtomicInteger n = new AtomicInteger(0);
        Gauge.builder("gauge", n, AtomicInteger::get).baseUnit("bytes").register(registry);
        assertThat(registry.scrape()).contains("gauge_bytes");
    }

    @DisplayName("percentiles are given as a separate sample with a key of 'quantile'")
    @Test
    void quantiles() {
        Timer.builder("timer")
            .publishPercentiles(0.5)
            .register(registry);
        DistributionSummary.builder("ds").publishPercentiles(0.5).register(registry);

        assertThat(prometheusRegistry.metricFamilySamples()).has(withNameAndTagKey("timer_duration_seconds", "quantile"));
        assertThat(prometheusRegistry.metricFamilySamples()).has(withNameAndTagKey("ds", "quantile"));
    }

    @DisplayName("custom distribution summaries respect varying tags")
    @Test
    /** Issue #27 */
    void customSummaries() {
        Arrays.asList("v1", "v2").forEach(v -> {
            registry.summary("s", "k", v).record(1.0);
            assertThat(registry.getPrometheusRegistry().getSampleValue("s_count", new String[]{"k"}, new String[]{v}))
                .describedAs("distribution summary s with a tag value of %s", v)
                .isEqualTo(1.0, offset(1e-12));
        });
    }

    @DisplayName("custom meters can be typed")
    @Test
    void typedCustomMeters() {
        Meter.builder("name", Meter.Type.Counter, Collections.singletonList(new Measurement(() -> 1.0, Statistic.Count)))
            .register(registry);

        assertThat(registry.getPrometheusRegistry().metricFamilySamples().nextElement().type)
            .describedAs("custom counter with a type of COUNTER")
            .isEqualTo(Collector.Type.COUNTER);
    }

    @DisplayName("attempts to register different meter types with the same name fail somewhat gracefully")
    @Test
    void differentMeterTypesWithSameName() {
        registry.timer("m");
        assertThrows(IllegalArgumentException.class, () -> registry.counter("m"));
    }

    @DisplayName("description text is bound to 'help' on Prometheus collectors")
    @Test
    void helpText() {
        Timer.builder("timer").description("my timer").register(registry);
        Counter.builder("counter").description("my counter").register(registry);
        DistributionSummary.builder("summary").description("my summary").register(registry);
        Gauge.builder("gauge", new AtomicInteger(), AtomicInteger::doubleValue).description("my gauge").register(registry);
        LongTaskTimer.builder("long.task.timer").description("my long task timer").register(registry);

        assertThat(registry.scrape())
            .contains("HELP timer_duration_seconds my timer")
            .contains("HELP summary my summary")
            .contains("HELP gauge my gauge")
            .contains("HELP counter_total my counter")
            .contains("HELP long_task_timer_duration_seconds my long task timer");
    }

    @Issue("#249")
    @Test
    void type() {
        Timer.builder("t1").register(registry);
        Timer.builder("t2").publishPercentileHistogram().register(registry);

        assertThat(registry.scrape())
            .contains("# TYPE t1_duration_seconds summary")
            .contains("# TYPE t2_duration_seconds histogram");
    }

    @Test
    void namingConventionOfCustomMeters() {
        CompositeMeterRegistry composite = new CompositeMeterRegistry();
        composite.add(registry);

        registry.more().counter("my.custom", emptyList(), 0);
        assertThat(registry.scrape())
            .contains("my_custom");
    }

    @Test
    void percentileTimersContainPositiveInfinity() {
        Timer timer = Timer.builder("my.timer").publishPercentileHistogram().register(registry);
        timer.record(1, TimeUnit.MILLISECONDS);

        assertThat(registry.scrape()).contains("le=\"+Inf\"");
    }

    @Test
    void percentileTimersAreClampedByDefault() {
        Timer timer = Timer.builder("my.timer")
            .publishPercentileHistogram()
            .register(registry);
        timer.record(1, TimeUnit.MILLISECONDS);

        assertThat(Arrays.stream(registry.scrape().split("\n")).filter(l -> l.contains("le=")))
            .hasSize(69);
    }

    @Issue("#127")
    @Test
    void percentileHistogramsAccumulateToInfinityEvenWhenClamped() {
        // the underlying histogram is clamped implicitly by HdrHistogram
        DistributionSummary widthSizes = DistributionSummary.builder("screen.width.pixels")
            .tags("page", "home")
            .description("Distribution of screen 'width'")
            .publishPercentileHistogram()
            .register(registry);

        widthSizes.record(1024);

        assertThat(registry.scrape())
            .contains("screen_width_pixels_bucket{page=\"home\",le=\"+Inf\",} 1.0");
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

        assertThat(registry.scrape())
            .contains("speed_index_bucket{page=\"home\",le=\"+Inf\",} 1.0");
    }

    @Issue("#61")
    @Test
    void timersRecordMax() {
        Timer timer = registry.timer("my.timer");
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(1, TimeUnit.SECONDS);

        assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(0);
        assertThat(registry.scrape()).contains("my_timer_duration_seconds_max 0.0");

        clock(registry).add(PrometheusConfig.DEFAULT.step());
        assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(1000);

        assertThat(registry.scrape()).contains("my_timer_duration_seconds_max 1.0");
    }

    @Issue("#61")
    @Test
    void distributionSummariesRecordMax() {
        DistributionSummary summary = registry.summary("my.summary");
        summary.record(10);
        summary.record(1);

        assertThat(summary.max()).isEqualTo(0);
        assertThat(registry.scrape()).contains("my_summary_max 0.0");

        clock(registry).add(PrometheusConfig.DEFAULT.step());
        assertThat(summary.max()).isEqualTo(10);

        assertThat(registry.scrape()).contains("my_summary_max 10.0");
    }

    @Issue("#246")
    @Test
    void functionCounterNamingConvention() {
        FunctionCounter.builder("api.requests", 1.0, n -> n).register(registry);

        assertThat(registry.scrape())
            .contains("api_requests_total 1.0");
    }

    private Condition<Enumeration<Collector.MetricFamilySamples>> withNameAndTagKey(String name, String tagKey) {
        return new Condition<>(m -> {
            while (m.hasMoreElements()) {
                Collector.MetricFamilySamples samples = m.nextElement();
                if (samples.samples.stream().anyMatch(s -> s.name.equals(name) && s.labelNames.contains(tagKey))) {
                    return true;
                }
            }
            return false;
        }, "a meter with name `%s` and tag `%s`", name, tagKey);
    }
}
