/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.newrelic;

import com.newrelic.api.agent.Agent;
import com.newrelic.api.agent.NewRelic;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Publishes metrics to New Relic Insights via Java Agent API.
 *
 * @author Neil Powell
 * @since 1.4.0
 */
public class NewRelicInsightsAgentClientProvider implements NewRelicClientProvider {

    private final Logger logger = LoggerFactory.getLogger(NewRelicInsightsAgentClientProvider.class);

    private final NewRelicConfig config;

    private final Agent newRelicAgent;

    // VisibleForTesting
    NamingConvention namingConvention;

    public NewRelicInsightsAgentClientProvider(NewRelicConfig config) {
        this(config, NewRelic.getAgent(), new NewRelicNamingConvention());
    }

    /**
     * Create a {@code NewRelicInsightsAgentClientProvider} instance.
     * @param config config
     * @param newRelicAgent New Relic agent
     * @since 1.4.2
     */
    public NewRelicInsightsAgentClientProvider(NewRelicConfig config, Agent newRelicAgent) {
        this(config, newRelicAgent, new NewRelicNamingConvention());
    }

    // VisibleForTesting
    NewRelicInsightsAgentClientProvider(NewRelicConfig config, Agent newRelicAgent, NamingConvention namingConvention) {
        config.requireValid();

        this.config = config;
        this.newRelicAgent = newRelicAgent;
        this.namingConvention = namingConvention;
    }

    @Override
    public void publish(NewRelicMeterRegistry meterRegistry) {
        // New Relic's Java Agent Insights API is backed by a reservoir/buffer
        // and handles the actual publishing of events to New Relic.
        // 1:1 mapping between Micrometer meters and New Relic events
        for (Meter meter : meterRegistry.getMeters()) {
            // @formatter:off
            sendEvents(meter.getId(), meter.match(
                    this::writeGauge,
                    this::writeCounter,
                    this::writeTimer,
                    this::writeSummary,
                    this::writeLongTaskTimer,
                    this::writeTimeGauge,
                    this::writeFunctionCounter,
                    this::writeFunctionTimer,
                    this::writeMeter));
            // @formatter:on
        }
    }

    @Override
    public Map<String, Object> writeLongTaskTimer(LongTaskTimer timer) {
        Map<String, Object> attributes = new HashMap<>();
        addAttribute(ACTIVE_TASKS, timer.activeTasks(), attributes);
        addAttribute(DURATION, timer.duration(timer.baseTimeUnit()), attributes);
        addAttribute(TIME_UNIT, timer.baseTimeUnit().name().toLowerCase(Locale.ROOT), attributes);
        // process meter's name, type and tags
        addMeterAsAttributes(timer.getId(), attributes);
        return attributes;
    }

    @Override
    public Map<String, Object> writeFunctionCounter(FunctionCounter counter) {
        return writeCounterValues(counter.getId(), counter.count());
    }

    @Override
    public Map<String, Object> writeCounter(Counter counter) {
        return writeCounterValues(counter.getId(), counter.count());
    }

    private Map<String, Object> writeCounterValues(Meter.Id id, double count) {
        if (!Double.isFinite(count)) {
            return Collections.emptyMap();
        }
        Map<String, Object> attributes = new HashMap<>();
        addAttribute(THROUGHPUT, count, attributes);
        // process meter's name, type and tags
        addMeterAsAttributes(id, attributes);
        return attributes;
    }

    @Override
    public Map<String, Object> writeGauge(Gauge gauge) {
        double value = gauge.value();
        if (!Double.isFinite(value)) {
            return Collections.emptyMap();
        }
        Map<String, Object> attributes = new HashMap<>();
        addAttribute(VALUE, value, attributes);
        // process meter's name, type and tags
        addMeterAsAttributes(gauge.getId(), attributes);
        return attributes;
    }

    @Override
    public Map<String, Object> writeTimeGauge(TimeGauge gauge) {
        double value = gauge.value();
        if (!Double.isFinite(value)) {
            return Collections.emptyMap();
        }
        Map<String, Object> attributes = new HashMap<>();
        addAttribute(VALUE, value, attributes);
        addAttribute(TIME_UNIT, gauge.baseTimeUnit().name().toLowerCase(Locale.ROOT), attributes);
        // process meter's name, type and tags
        addMeterAsAttributes(gauge.getId(), attributes);
        return attributes;
    }

    @Override
    public Map<String, Object> writeSummary(DistributionSummary summary) {
        Map<String, Object> attributes = new HashMap<>();
        addAttribute(COUNT, summary.count(), attributes);
        addAttribute(AVG, summary.mean(), attributes);
        addAttribute(TOTAL, summary.totalAmount(), attributes);
        addAttribute(MAX, summary.max(), attributes);
        // process meter's name, type and tags
        addMeterAsAttributes(summary.getId(), attributes);
        return attributes;
    }

    @Override
    public Map<String, Object> writeTimer(Timer timer) {
        Map<String, Object> attributes = new HashMap<>();
        TimeUnit timeUnit = timer.baseTimeUnit();
        addAttribute(COUNT, timer.count(), attributes);
        addAttribute(AVG, timer.mean(timeUnit), attributes);
        addAttribute(TOTAL_TIME, timer.totalTime(timeUnit), attributes);
        addAttribute(MAX, timer.max(timeUnit), attributes);
        addAttribute(TIME_UNIT, timeUnit.name().toLowerCase(Locale.ROOT), attributes);
        // process meter's name, type and tags
        addMeterAsAttributes(timer.getId(), attributes);
        return attributes;
    }

    @Override
    public Map<String, Object> writeFunctionTimer(FunctionTimer timer) {
        Map<String, Object> attributes = new HashMap<>();
        TimeUnit timeUnit = timer.baseTimeUnit();
        addAttribute(COUNT, timer.count(), attributes);
        addAttribute(AVG, timer.mean(timeUnit), attributes);
        addAttribute(TOTAL_TIME, timer.totalTime(timeUnit), attributes);
        addAttribute(TIME_UNIT, timeUnit.name().toLowerCase(Locale.ROOT), attributes);
        // process meter's name, type and tags
        addMeterAsAttributes(timer.getId(), attributes);
        return attributes;
    }

    @Override
    public Map<String, Object> writeMeter(Meter meter) {
        Map<String, Object> attributes = new HashMap<>();
        for (Measurement measurement : meter.measure()) {
            double value = measurement.getValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            addAttribute(measurement.getStatistic().getTagValueRepresentation(), value, attributes);
        }
        if (attributes.isEmpty()) {
            return attributes;
        }
        // process meter's name, type and tags
        addMeterAsAttributes(meter.getId(), attributes);
        return attributes;
    }

    private void addMeterAsAttributes(Meter.Id id, Map<String, Object> attributes) {
        if (!config.meterNameEventTypeEnabled()) {
            // Include contextual attributes when publishing all metrics under a single
            // categorical eventType,
            // NOT when publishing an eventType per Meter/metric name
            String name = id.getConventionName(namingConvention);
            attributes.put(METRIC_NAME, name);
            attributes.put(METRIC_TYPE, id.getType().toString());
        }
        // process meter tags
        for (Tag tag : id.getConventionTags(namingConvention)) {
            attributes.put(tag.getKey(), tag.getValue());
        }
    }

    private void addAttribute(String key, Number value, Map<String, Object> attributes) {
        // process other tags

        // Replicate DoubleFormat.wholeOrDecimal(value.doubleValue()) formatting behavior
        if (Math.floor(value.doubleValue()) == value.doubleValue()) {
            // whole number - don't include decimal
            attributes.put(namingConvention.tagKey(key), value.intValue());
        }
        else {
            // include decimal
            attributes.put(namingConvention.tagKey(key), value.doubleValue());
        }
    }

    private void addAttribute(String key, String value, Map<String, Object> attributes) {
        // process other tags
        attributes.put(namingConvention.tagKey(key), namingConvention.tagValue(value));
    }

    void sendEvents(Meter.Id id, Map<String, Object> attributes) {
        // Delegate to New Relic Java Agent
        if (attributes != null && !attributes.isEmpty()) {
            String eventType = getEventType(id, config, namingConvention);
            try {
                newRelicAgent.getInsights().recordCustomEvent(eventType, attributes);
            }
            catch (Throwable e) {
                logger.warn("failed to send metrics to new relic", e);
            }
        }
    }

    @Override
    public void setNamingConvention(NamingConvention namingConvention) {
        this.namingConvention = namingConvention;
    }

}
