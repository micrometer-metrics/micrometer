package org.springframework.metrics.instrument.prometheus;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.LongTaskTimer;
import org.springframework.metrics.instrument.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.singletonList;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;

public class PrometheusLongTaskTimer extends Collector implements LongTaskTimer {
    private final ConcurrentMap<Long, Long> tasks = new ConcurrentHashMap<>();
    private final AtomicLong nextTask = new AtomicLong(0L);

    private String name;
    private Iterable<Tag> tags;
    private final Clock clock;

    public PrometheusLongTaskTimer(String name, Iterable<Tag> tags, Clock clock) {
        this.tags = tags;
        this.clock = clock;
        this.name = name;
    }

    @Override
    public long start() {
        long task = nextTask.getAndIncrement();
        tasks.put(task, clock.monotonicTime());
        return task;
    }

    @Override
    public long stop(long task) {
        Long startTime = tasks.get(task);
        if (startTime != null) {
            tasks.remove(task);
            return clock.monotonicTime() - startTime;
        } else {
            return -1L;
        }
    }

    @Override
    public long duration(long task) {
        Long startTime = tasks.get(task);
        return (startTime != null) ? (clock.monotonicTime() - startTime) : -1L;
    }

    @Override
    public long duration() {
        long now = clock.monotonicTime();
        long sum = 0L;
        for (long startTime : tasks.values()) {
            sum += now - startTime;
        }
        return sum;
    }

    @Override
    public int activeTasks() {
        return tasks.size();
    }

    @Override
    public List<MetricFamilySamples> collect() {
        Stream<Tag> tagStream = StreamSupport.stream(tags.spliterator(), false);

        List<String> tagNames = concat(tagStream.map(Tag::getKey), of("statistic")).collect(Collectors.toList());

        GaugeMetricFamily labeledGauge = new GaugeMetricFamily(name, " ", tagNames);
        labeledGauge.addMetric(concat(tagStream.map(Tag::getValue), of("activeTasks")).collect(Collectors.toList()), activeTasks());
        labeledGauge.addMetric(concat(tagStream.map(Tag::getValue), of("duration")).collect(Collectors.toList()), duration());

        return singletonList(labeledGauge);
    }
}
