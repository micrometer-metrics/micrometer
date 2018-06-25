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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.jersey2.server.mapper.ResourceGoneExceptionMapper;
import io.micrometer.jersey2.server.resources.TestResource;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Application;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michael Weirauch
 */
public class MetricsRequestEventListenerTest extends JerseyTest {

    static {
        Logger.getLogger("org.glassfish.jersey").setLevel(Level.OFF);
    }

    private static final String METRIC_NAME = "http.server.requests";

    private MeterRegistry registry;

    @Override
    protected Application configure() {
        registry = new SimpleMeterRegistry();

        final MetricsApplicationEventListener listener = new MetricsApplicationEventListener(
            registry, new DefaultJerseyTagsProvider(), METRIC_NAME, true);

        final ResourceConfig config = new ResourceConfig();
        config.register(listener);
        config.register(TestResource.class);
        config.register(ResourceGoneExceptionMapper.class);

        return config;
    }

    @Test
    public void resourcesAreTimed() {
        target("/").request().get();
        target("hello").request().get();
        target("hello/").request().get();
        target("hello/peter").request().get();
        target("sub-resource/sub-hello/peter").request().get();

        assertThat(registry.get(METRIC_NAME)
            .tags(tagsFrom("/", 200, null)).timer().count())
            .isEqualTo(1);

        assertThat(registry.get(METRIC_NAME)
            .tags(tagsFrom("/hello", 200, null)).timer().count())
            .isEqualTo(2);

        assertThat(registry.get(METRIC_NAME)
            .tags(tagsFrom("/hello/{name}", 200, null)).timer().count())
            .isEqualTo(1);

        assertThat(registry.get(METRIC_NAME)
            .tags(tagsFrom("/sub-resource/sub-hello/{name}", 200, null)).timer().count())
            .isEqualTo(1);

        // assert we are not auto-timing long task @Timed
        assertThat(registry.getMeters()).hasSize(4);
    }

    @Test
    public void notFoundIsAccumulatedUnderSameUri() {
        try {
            target("not-found").request().get();
        } catch (NotFoundException ignored) {
        }
        try {
            target("throws-not-found-exception").request().get();
        } catch (NotFoundException ignored) {
        }

        assertThat(registry.get(METRIC_NAME)
            .tags(tagsFrom("NOT_FOUND", 404, null)).timer().count())
            .isEqualTo(2);
    }

    @Test
    public void redirectsAreAccumulatedUnderSameUri() {
        target("redirect/302").request().get();
        target("redirect/307").request().get();

        assertThat(registry.get(METRIC_NAME)
            .tags(tagsFrom("REDIRECTION", 302, null)).timer().count())
            .isEqualTo(1);

        assertThat(registry.get(METRIC_NAME)
            .tags(tagsFrom("REDIRECTION", 307, null)).timer().count())
            .isEqualTo(1);
    }

    @Test
    public void exceptionsAreMappedCorrectly() {
        try {
            target("throws-exception").request().get();
        } catch (Exception ignored) {
        }
        try {
            target("throws-webapplication-exception").request().get();
        } catch (Exception ignored) {
        }
        try {
            target("throws-mappable-exception").request().get();
        } catch (Exception ignored) {
        }

        assertThat(registry.get(METRIC_NAME)
            .tags(tagsFrom("/throws-exception", 500, "IllegalArgumentException"))
            .timer().count())
            .isEqualTo(1);

        assertThat(registry.get(METRIC_NAME).tags(
            tagsFrom("/throws-webapplication-exception", 401, "NotAuthorizedException"))
            .timer().count())
            .isEqualTo(1);

        assertThat(registry.get(METRIC_NAME).tags(
            tagsFrom("/throws-mappable-exception", 410, "ResourceGoneException"))
            .timer().count())
            .isEqualTo(1);
    }

    private static Iterable<Tag> tagsFrom(String uri, int status, String exception) {
        return Tags.of(DefaultJerseyTagsProvider.TAG_METHOD, "GET",
            DefaultJerseyTagsProvider.TAG_URI, uri, DefaultJerseyTagsProvider.TAG_STATUS,
            String.valueOf(status), DefaultJerseyTagsProvider.TAG_EXCEPTION,
            exception == null ? "None" : exception);
    }
}
