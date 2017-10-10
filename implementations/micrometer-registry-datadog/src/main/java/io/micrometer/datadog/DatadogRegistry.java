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
package io.micrometer.datadog;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Tag;
import io.micrometer.core.instrument.spectator.step.AbstractStepRegistry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;

/**
 * Registry for reporting metrics to Datadog.
 *
 * @author Jon Schneider
 */
final class DatadogRegistry extends AbstractStepRegistry {

    private final URL metricsEndpoint;
    private final String hostTag;

    public DatadogRegistry(DatadogConfig config, Clock clock) {
        super(config, clock);

        try {
            this.metricsEndpoint = URI.create(config.uri()).toURL();
        } catch (MalformedURLException e) {
            // not possible
            throw new RuntimeException(e);
        }

        this.hostTag = config.hostTag();
    }

    protected void pushMetrics() {
        try {
            for (List<Measurement> batch : getBatches()) {
                HttpURLConnection con = (HttpURLConnection) metricsEndpoint.openConnection();
                con.setConnectTimeout(connectTimeout);
                con.setReadTimeout(readTimeout);
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);

                /*
                Example post body from Datadog API docs. Type seems to be irrelevant. Host and tags are optional.
                "{ \"series\" :
                        [{\"metric\":\"test.metric\",
                          \"points\":[[$currenttime, 20]],
                          \"type\":\"gauge\",
                          \"host\":\"test.example.com\",
                          \"tags\":[\"environment:test\"]}
                        ]
                }"
                */

                String body = "{\"series\":[" +
                        batch.stream().map(m -> {
                            Iterable<Tag> tags = m.id().tags();

                            String host = hostTag == null ? "" : StreamSupport.stream(tags.spliterator(), false)
                                    .filter(t -> hostTag.equals(t.key()))
                                    .findAny()
                                    .map(t -> ",\"host\":" + t.value())
                                    .orElse("");

                            String tagsArray = tags.iterator().hasNext() ?
                                    ",\"tags\":[" +
                                            StreamSupport.stream(tags.spliterator(), false)
                                                    .map(t -> "\"" + t.key() + ":" + t.value() + "\"")
                                                    .collect(joining(",")) + "]" : "";

                            return "{\"metric\":\"" + m.id().name() + "\"," +
                                    "\"points\":[[" + (m.timestamp() / 1000) + ", " + m.value() + "]]" +
                                    host + tagsArray +
                                    "}";
                        }).collect(joining(",")) +
                        "]}";

                try (OutputStream os = con.getOutputStream()) {
                    os.write(body.getBytes());
                    os.flush();
                }

                int status = con.getResponseCode();

                if (status >= 200 && status < 300) {
                    logger.info("successfully sent " + batch.size() + " metrics to datadog");
                } else if (status >= 400) {
                    try (InputStream in = con.getErrorStream()) {
                        logger.error("failed to send metrics: " + new BufferedReader(new InputStreamReader(in))
                                .lines().collect(joining("\n")));
                    }
                } else {
                    logger.error("failed to send metrics: http " + status);
                }

                con.disconnect();
            }
        } catch (Exception e) {
            logger.warn("failed to send metrics", e);
        }
    }
}
