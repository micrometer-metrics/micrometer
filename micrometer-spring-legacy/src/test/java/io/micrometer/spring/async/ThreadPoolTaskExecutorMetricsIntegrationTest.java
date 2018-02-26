package io.micrometer.spring.async;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.spring.autoconfigure.MeterRegistryPostProcessor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit4.SpringRunner;

import static java.util.Collections.emptyList;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ThreadPoolTaskExecutorMetricsIntegrationTest.App.class)
public class ThreadPoolTaskExecutorMetricsIntegrationTest {

    @Autowired
    MeterRegistry registry;

    @Issue("#459")
    @Test
    public void executorMetricsPhase() {
        registry.get("jvm.memory.max").gauge();
    }

    @SpringBootApplication(scanBasePackages = "ignore")
    @Import(AsyncExecutorConfig.class)
    static class App {
        @Bean
        public AsyncTaskExecutor executor(MeterRegistry registry) {
            return new TimedThreadPoolTaskExecutor(registry, "threads", emptyList());
        }
    }

    /**
     * Injecting the registry instead would cause early evaluation of the registry and the registry
     * wouldn't be discovered by {@link MeterRegistryPostProcessor}.
     */
    @Configuration
    @EnableAsync
    static class AsyncExecutorConfig extends AsyncConfigurerSupport {
        @Bean
        public AsyncTaskExecutor getAsyncExecutor() {
            final ThreadPoolTaskExecutor executor = new TimedThreadPoolTaskExecutor(Metrics.globalRegistry, "workers", emptyList());
            executor.initialize();
            return executor;
        }
    }
}
