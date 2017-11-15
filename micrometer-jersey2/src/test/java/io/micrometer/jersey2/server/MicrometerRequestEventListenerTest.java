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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Application;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.jersey2.server.resources.TestResource;

/**
 * @author Michael Weirauch
 */
public class MicrometerRequestEventListenerTest extends JerseyTest {

    static {
        Logger.getLogger("org.glassfish.jersey").setLevel(Level.OFF);
    }

    private static final String METRIC_NAME = "http.server.requests";

    private MeterRegistry registry;

    @Override
    protected Application configure() {
        registry = new SimpleMeterRegistry();

        final MicrometerApplicationEventListener listener = new MicrometerApplicationEventListener(
                registry, new DefaultJerseyTagsProvider(), METRIC_NAME);

        final ResourceConfig config = new ResourceConfig();
        config.register(listener);
        config.register(TestResource.class);

        return config;
    }

    @Test
    public void resourcesAreTimed() {
        target("/").request().get();
        target("hello").request().get();
        target("hello/").request().get();
        target("hello/peter").request().get();
        target("sub-resource/sub-hello/peter").request().get();

        Optional<Timer> timerIndex = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "/", 200, null)).timer();
        assertThat(timerIndex).hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));

        Optional<Timer> timerHello = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "/hello", 200, null)).timer();
        assertThat(timerHello).hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(2));

        Optional<Timer> timerName = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "/hello/{name}", 200, null)).timer();
        assertThat(timerName).hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));

        Optional<Timer> timerSubName = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "/sub-resource/sub-hello/{name}", 200, null)).timer();
        assertThat(timerSubName).hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @Test
    public void notFoundIsAccumulatedUnderSameUri() {
        try {
            target("not-found").request().get();
        } catch (NotFoundException nfe) {
            //
        }
        try {
            target("throws-not-found-exception").request().get();
        } catch (NotFoundException nfe) {
            //
        }

        Optional<Timer> timer = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "NOT_FOUND", 404, null)).timer();
        assertThat(timer).hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(2));
    }

    @Test
    public void redirectsAreAccumulatedUnderSameUri() {
        target("redirect/302").request().get();
        target("redirect/307").request().get();

        Optional<Timer> timer302 = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "REDIRECTION", 302, null)).timer();
        assertThat(timer302).hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));

        Optional<Timer> timer307 = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "REDIRECTION", 307, null)).timer();
        assertThat(timer307).hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @Test
    public void exceptionsAreMappedCorrectly() {
        try {
            target("throws-exception").request().get();
        } catch (Exception e) {
            //
        }
        try {
            target("throws-webapplication-exception").request().get();
        } catch (Exception e) {
            //
        }

        Optional<Timer> timerException = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "/throws-exception", 500, "IllegalArgumentException"))
                .timer();
        assertThat(timerException).hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));

        Optional<Timer> timerWebApplicationException = registry.find(METRIC_NAME).tags(
                tagsFrom("GET", "/throws-webapplication-exception", 401, "NotAuthorizedException"))
                .timer();
        assertThat(timerWebApplicationException)
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    private static Iterable<Tag> tagsFrom(String method, String uri, int status, String exception) {
        return Tags.zip(DefaultJerseyTagsProvider.TAG_METHOD, method,
                DefaultJerseyTagsProvider.TAG_URI, uri, DefaultJerseyTagsProvider.TAG_STATUS,
                String.valueOf(status), DefaultJerseyTagsProvider.TAG_EXCEPTION,
                exception == null ? "None" : exception);
    }

}
