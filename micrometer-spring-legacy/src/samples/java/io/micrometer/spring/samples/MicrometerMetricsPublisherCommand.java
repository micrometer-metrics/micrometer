package io.micrometer.spring.samples;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.metric.consumer.CumulativeCommandEventCounterStream;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
        tags = Tags.zip("group", commandGroupKey.name(), "key", commandKey.name());
    }

    @Override
    public void initialize() {
        Gauge.builder("hystrix.circuit.breaker.open", circuitBreaker, c -> c.isOpen() ? 1 : 0)
            .tags(tags).register(meterRegistry);

        String executionName = "hystrix.execution";
        String executionDescription = "Execution results. See https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#command-execution-event-types-comnetflixhystrixhystrixeventtype for type definitions";
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.EMIT);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.SUCCESS);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.FAILURE);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.TIMEOUT);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.BAD_REQUEST);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.SHORT_CIRCUITED);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.SEMAPHORE_REJECTED);


        String fallbackEventName = "hystrix.fallback";
        String fallbackEventDescription = "Fallback execution results. See https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#command-fallback-event-types-comnetflixhystrixhystrixeventtype for type definitions";
        createCounter(fallbackEventName, fallbackEventDescription, HystrixRollingNumberEvent.FALLBACK_EMIT);
        createCounter(fallbackEventName, fallbackEventDescription, HystrixRollingNumberEvent.FALLBACK_SUCCESS);
        createCounter(fallbackEventName, fallbackEventDescription, HystrixRollingNumberEvent.FALLBACK_FAILURE);
        createCounter(fallbackEventName, fallbackEventDescription, HystrixRollingNumberEvent.FALLBACK_REJECTION);
        createCounter(fallbackEventName, fallbackEventDescription, HystrixRollingNumberEvent.FALLBACK_MISSING);

//        RollingCommandEventCounterStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
        CumulativeCommandEventCounterStream.getInstance(commandKey, properties).startCachingStreamValuesIfUnstarted();
//        RollingCommandLatencyDistributionStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
//        RollingCommandUserLatencyDistributionStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
//        RollingCommandMaxConcurrencyStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();

    }

    private void createCounter(String name, String executionDescription, HystrixRollingNumberEvent event) {


        meterRegistry.more().counter(meterRegistry.createId(name, Tags.concat(tags, "event", event.name().toLowerCase()), executionDescription), metrics, m -> {
                try {
                    return m.getCumulativeCount(event);
                } catch (NoSuchFieldError error) {
                    LOG.error("While publishing metrics, error looking up eventType for : {}.  Please check that all Hystrix versions are the same!", name);
                    return 0L;
                }
        });
    }
}
