package io.micrometer.benchmark.core;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.micrometer.statsd.internal.BufferingFlux;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
public class BufferingFluxBenchmark {

    static final String[] FAKE_WORDS = {
            "Hello_world",
            "world",
            "Hello_Hello_hello_world"
    };

    @Param({ "100000" })
    public int times;

    @Param({ "10",  "100", "1000"})
    public int size;

    Flux<String> source;

    @Setup
    public void setup() {
        source = Flux.range(0, times).map(i -> FAKE_WORDS[i % 3]);
    }

    @Benchmark
    public void bufferingFlux() {
        source
                .transform(f -> BufferingFlux.create(f, "\n", size, 100))
                .blockLast();
    }

    @Benchmark
    public void bufferingFluxOld() {
        source
                .transform(f -> create(f, "\n", size, 100))
                .blockLast();
    }


    static Flux<String> create(final Flux<String> source, final String delimiter, final int maxByteArraySize, final long maxMillisecondsBetweenEmits) {
        return Flux.defer(() -> {
            final int delimiterSize = delimiter.getBytes().length;
            final AtomicInteger byteSize = new AtomicInteger();
            final AtomicLong lastTime = new AtomicLong();

            final Sinks.Empty<Void> intervalEnd = Sinks.empty();

            final Flux<String> heartbeat = Flux.interval(Duration.ofMillis(maxMillisecondsBetweenEmits))
                                               .map(l -> "")
                                               .takeUntilOther(intervalEnd.asMono());

            // Create a stream that emits at least once every $maxMillisecondsBetweenEmits, to avoid long pauses between
            // buffer flushes when the source doesn't emit for a while.
            final Flux<String> sourceWithEmptyStringKeepAlive = source
                    .doOnTerminate(intervalEnd::tryEmitEmpty)
                    .mergeWith(heartbeat);

            return sourceWithEmptyStringKeepAlive
                    .bufferUntil(line -> {
                        final int bytesLength = line.getBytes().length;
                        final long now = System.currentTimeMillis();
                        // Update last time to now if this is the first time
                        lastTime.compareAndSet(0, now);
                        final long last = lastTime.get();
                        long diff;
                        if (last != 0L) {
                            diff = now - last;
                            if (diff > maxMillisecondsBetweenEmits && byteSize.get() > 0) {
                                // This creates a buffer, reset size
                                byteSize.set(bytesLength);
                                lastTime.compareAndSet(last, now);
                                return true;
                            }
                        }

                        int additionalBytes = bytesLength;
                        if (additionalBytes > 0 && byteSize.get() > 0) {
                            additionalBytes += delimiterSize;  // Make up for the delimiter that's added when joining the strings
                        }

                        final int projectedBytes = byteSize.addAndGet(additionalBytes);

                        if (projectedBytes > maxByteArraySize) {
                            // This creates a buffer, reset size
                            byteSize.set(bytesLength);
                            lastTime.compareAndSet(last, now);
                            return true;
                        }

                        return false;
                    }, true)
                    .map(lines -> lines.stream()
                                       .filter(line -> !line.isEmpty())
                                       .collect(Collectors.joining(delimiter, "", delimiter)));
        });
    }
}
