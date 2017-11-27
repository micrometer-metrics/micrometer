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
package io.micrometer.jersey2.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.jersey2.server.resources.TimedResource;

/**
 * @author Michael Weirauch
 */
public class MicrometerRequestEventListenerTimedTest extends JerseyTest {

    static {
        Logger.getLogger("org.glassfish.jersey").setLevel(Level.OFF);
    }

    private static final String METRIC_NAME = "http.server.requests";

    private MeterRegistry registry;

    private CountDownLatch longTaskRequestStartedLatch;

    private CountDownLatch longTaskRequestReleaseLatch;

    @Override
    protected Application configure() {
        registry = new SimpleMeterRegistry();
        longTaskRequestStartedLatch = new CountDownLatch(1);
        longTaskRequestReleaseLatch = new CountDownLatch(1);

        final MicrometerApplicationEventListener listener = new MicrometerApplicationEventListener(
                registry, new DefaultJerseyTagsProvider(), METRIC_NAME, false, false);

        final ResourceConfig config = new ResourceConfig();
        config.register(listener);
        config.register(
                new TimedResource(longTaskRequestStartedLatch, longTaskRequestReleaseLatch));

        return config;
    }

    @Test
    public void resourcesAndNotFoundsAreNotAutoTimed() {
        target("not-timed").request().get();
        target("not-found").request().get();

        Optional<Timer> notTimed = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "/not-timed", 200, null)).timer();
        assertThat(notTimed).isEmpty();

        Optional<Timer> notFound = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "NOT_FOUND", 404, null)).timer();
        assertThat(notFound).isEmpty();
    }

    @Test
    public void resourcesWithAnnotationAreTimed() {
        target("timed").request().get();
        target("multi-timed").request().get();

        Optional<Timer> timed = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "/timed", 200, null)).timer();
        assertThat(timed).hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));

        Optional<Timer> multiTimed1 = registry.find("multi1")
                .tags(tagsFrom("GET", "/multi-timed", 200, null)).timer();
        assertThat(multiTimed1).hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));

        Optional<Timer> multiTimed2 = registry.find("multi2")
                .tags(tagsFrom("GET", "/multi-timed", 200, null)).timer();
        assertThat(multiTimed2).hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @Test
    public void longTaskTimerSupported() throws InterruptedException, ExecutionException {
        final Future<Response> future = target("long-timed").request().async().get();

        /*
         * Wait until the request has arrived at the server side. (Async client
         * processing might be slower in triggering the request resulting in the
         * assertions below to fail. Thread.sleep() is not an option, so resort
         * to CountDownLatch.)
         */
        longTaskRequestStartedLatch.await();

        // the request is not timed, yet
        assertThat(registry.find(METRIC_NAME).tags(tagsFrom("GET", "/timed", 200, null)).timer())
                .isEmpty();

        // the long running task is timed
        assertThat(registry.find("long.task.in.request")
                .tags(Tags.zip(DefaultJerseyTagsProvider.TAG_METHOD, "GET",
                        DefaultJerseyTagsProvider.TAG_URI, "/long-timed"))
                .value(Statistic.Count, 1.0).longTaskTimer()).isPresent();

        // finish the long running request
        longTaskRequestReleaseLatch.countDown();
        future.get();

        // the request is timed after the long running request completed
        assertThat(registry.find(METRIC_NAME).tags(tagsFrom("GET", "/long-timed", 200, null))
                .value(Statistic.Count, 1.0).timer()).isPresent();
    }

    @Test
    public void unnamedlongTaskTimerIsNotMetered() {
        target("long-timed-unnamed").request().get();

        // the request is timed
        assertThat(
                registry.find(METRIC_NAME).tags(tagsFrom("GET", "/long-timed-unnamed", 200, null))
                        .value(Statistic.Count, 1.0).timer()).isPresent();

        // no other metric is present (the long task timer is not started due to
        // missing name)
        assertThat(registry.getMeters()).hasSize(1);
    }

    private static Iterable<Tag> tagsFrom(String method, String uri, int status, String exception) {
        return Tags.zip(DefaultJerseyTagsProvider.TAG_METHOD, method,
                DefaultJerseyTagsProvider.TAG_URI, uri, DefaultJerseyTagsProvider.TAG_STATUS,
                String.valueOf(status), DefaultJerseyTagsProvider.TAG_EXCEPTION,
                exception == null ? "None" : exception);
    }

}
