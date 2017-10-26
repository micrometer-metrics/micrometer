package io.micrometer.spring.samples;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCollapser;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerMetricsPublisher extends HystrixMetricsPublisher {

    private final MeterRegistry meterRegistry;

    public MicrometerMetricsPublisher(MeterRegistry meterRegistry) {

        this.meterRegistry = meterRegistry;
    }

    @Override
    public HystrixMetricsPublisherCommand getMetricsPublisherForCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        return new MicrometerMetricsPublisherCommand(meterRegistry, commandKey, commandGroupKey, metrics, circuitBreaker, properties);
    }
}
