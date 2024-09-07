package io.micrometer.java21.instrument.binder.jfr;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.awaitility.Awaitility.await;

class JfrVirtualThreadEventMetricsTests {

    private static final Tags USER_TAGS = Tags.of("k", "v");

    private MeterRegistry registry;

    private JfrVirtualThreadEventMetrics jfrMetrics;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        jfrMetrics = new JfrVirtualThreadEventMetrics(USER_TAGS);
        jfrMetrics.bindTo(registry);
    }

    @Test
    void registerPinnedEvent() throws Exception {
        Thread.ofVirtual()
            .name("vt-test")
            .start(() -> {
                synchronized (this) {
                    sleep(Duration.ofMillis(100));
                }
            })
            .join();

        await().atMost(Duration.ofSeconds(2))
            .until(() -> registry.get("jvm.virtual.thread.pinned").timer().count() == 1);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        }
        catch (InterruptedException ignored) {
        }
    }

    @AfterEach
    void cleanup() throws IOException {
        jfrMetrics.close();
    }

}
