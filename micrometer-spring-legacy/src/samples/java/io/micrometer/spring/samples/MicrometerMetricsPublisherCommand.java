package io.micrometer.spring.samples;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.metric.consumer.CumulativeCommandEventCounterStream;
import com.netflix.hystrix.metric.consumer.RollingCommandEventCounterStream;
import com.netflix.hystrix.metric.consumer.RollingCommandLatencyDistributionStream;
import com.netflix.hystrix.metric.consumer.RollingCommandMaxConcurrencyStream;
import com.netflix.hystrix.metric.consumer.RollingCommandUserLatencyDistributionStream;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MicrometerMetricsPublisherCommand implements HystrixMetricsPublisherCommand {
    private static final Logger LOG = LoggerFactory.getLogger(MicrometerMetricsPublisherCommand.class);

    private final MeterRegistry meterRegistry;
    private final HystrixCommandMetrics metrics;
    private final HystrixCircuitBreaker circuitBreaker;
    private final List<Tag> tags;
    private final HystrixCommandProperties properties;
    private final HystrixCommandKey commandKey;

    public MicrometerMetricsPublisherCommand(MeterRegistry meterRegistry, HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        this.meterRegistry = meterRegistry;
        this.metrics = metrics;
        this.circuitBreaker = circuitBreaker;
        this.commandKey = commandKey;
        this.properties = properties;
        tags = Tags.zip("commandGroupKey", commandGroupKey.name(), "key", commandKey.name());
    }

    @Override
    public void initialize() {
        Gauge.builder("hystrix.circuit.breaker.open", circuitBreaker, c -> c.isOpen() ? 1 : 0)
            .tags(tags).register(meterRegistry);

        createCounter("hystrix.circuit.breaker.bad.requests", HystrixRollingNumberEvent.BAD_REQUEST);
        createCounter("hystrix.circuit.breaker.short.circuited", HystrixRollingNumberEvent.SHORT_CIRCUITED);
        createCounter("hystrix.circuit.breaker.success", HystrixRollingNumberEvent.SUCCESS);
        createCounter("hystrix.circuit.breaker.failure", HystrixRollingNumberEvent.FALLBACK_SUCCESS);

//        RollingCommandEventCounterStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
        CumulativeCommandEventCounterStream.getInstance(commandKey, properties).startCachingStreamValuesIfUnstarted();
//        RollingCommandLatencyDistributionStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
//        RollingCommandUserLatencyDistributionStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
//        RollingCommandMaxConcurrencyStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();

    }

    private void createCounter(String name, HystrixRollingNumberEvent event) {


        meterRegistry.more().counter(meterRegistry.createId(name, tags, " "), metrics, m -> {
                try {
                    return m.getCumulativeCount(event);
                } catch (NoSuchFieldError error) {
                    LOG.error("While publishing metrics, error looking up eventType for : {}.  Please check that all Hystrix versions are the same!", name);
                    return 0L;
                }
        });
    }
}
