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
public class MicrometerRequestEventListenerTimedTest extends JerseyTest {

    static {
        Logger.getLogger("org.glassfish.jersey").setLevel(Level.OFF);
    }

    private static final String METRIC_NAME = "http.server.requests";

    private MeterRegistry registry;

    @Override
    protected Application configure() {
        registry = new SimpleMeterRegistry();

        final MicrometerApplicationEventListener listener = new MicrometerApplicationEventListener(
                registry, new DefaultJerseyTagsProvider(), METRIC_NAME, false);

        final ResourceConfig config = new ResourceConfig();
        config.register(listener);
        config.register(TestResource.class);

        return config;
    }

    @Test
    public void resourcesAreNotAutoTimed() {
        target("hello").request().get();

        Optional<Timer> timerHello = registry.find(METRIC_NAME)
                .tags(tagsFrom("GET", "/hello", 200, null)).timer();
        assertThat(timerHello).isEmpty();
    }

    private static Iterable<Tag> tagsFrom(String method, String uri, int status, String exception) {
        return Tags.zip(DefaultJerseyTagsProvider.TAG_METHOD, method,
                DefaultJerseyTagsProvider.TAG_URI, uri, DefaultJerseyTagsProvider.TAG_STATUS,
                String.valueOf(status), DefaultJerseyTagsProvider.TAG_EXCEPTION,
                exception == null ? "None" : exception);
    }

}
