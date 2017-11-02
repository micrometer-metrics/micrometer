package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.assertj.core.api.Assertions.assertThat;

class OkHttpMetricsEventListenerTest {
    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private OkHttpClient client = new OkHttpClient.Builder()
        .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
            .uriMapper(req -> req.url().encodedPath())
            .tags(Tags.zip("foo", "bar"))
            .build())
        .build();

    @Test
    void timeSuccessful() throws IOException {
        Request request = new Request.Builder()
            .url("https://publicobject.com/helloworld.txt")
            .build();

        client.newCall(request).execute().close();

        clock(registry).add(SimpleConfig.DEFAULT_STEP);
        assertThat(registry.find("okhttp.requests")
            .tags("uri", "/helloworld.txt")
            .tags("status", "200")
            .timer().map(Timer::count)).isPresent().hasValue(1L);
    }

    @Test
    void timeNotFound() {
        Request request = new Request.Builder()
            .url("https://publicobject.com/DOESNOTEXIST")
            .build();

        try {
            client.newCall(request).execute().close();
        } catch(IOException ignore) {
            // expected
        }

        clock(registry).add(SimpleConfig.DEFAULT_STEP);
        assertThat(registry.find("okhttp.requests")
            .tags("uri", "NOT_FOUND")
            .timer().map(Timer::count)).isPresent().hasValue(1L);
    }

    @Test
    void timeFailureDueToTimeout() {
        Request request = new Request.Builder()
            .url("https://publicobject.com/helloworld.txt")
            .build();

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.MILLISECONDS)
            .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                .uriMapper(req -> req.url().encodedPath())
                .tags(Tags.zip("foo", "bar"))
                .build())
            .build();

        try {
            client.newCall(request).execute().close();
        } catch(IOException ignored) {
            // expected
        }

        clock(registry).add(SimpleConfig.DEFAULT_STEP);
        assertThat(registry.find("okhttp.requests")
            .tags("uri", "UNKNOWN")
            .tags("status", "IO_ERROR")
            .timer().map(Timer::count)).isPresent().hasValue(1L);
    }
}