/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.influx;

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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPOutputStream;

import static java.util.stream.Collectors.joining;

/**
 * @author Jon Schneider
 */
public class InfluxRegistry extends AbstractStepRegistry {
    private final URL influxEndpoint;
    private final String userName;
    private final String password;
    private final boolean compressed;

    public InfluxRegistry(InfluxConfig config, Clock clock) {
        super(config, clock);

        try {
            String queryParams = "?consistency=" + config.consistency() + "&precision=ms&db=" + config.db();
            if(config.retentionPolicy() != null)
                queryParams += "&rp=" + config.retentionPolicy();
            this.influxEndpoint = URI.create(config.uri() + queryParams).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed InfluxDB publishing endpoint, see '" + config.prefix() + ".uri'", e);
        }

        this.userName = config.userName();
        this.password = config.password();
        this.compressed = config.compressed();
    }

    @Override
    protected void pushMetrics() {
        try {
            // See note in InfluxDB Line Protocol Tuturial doc about assumption that hosts are synchronized with NTP.
            long time = clock().wallTime();

            for (List<Measurement> batch : getBatches()) {
                HttpURLConnection con = (HttpURLConnection) influxEndpoint.openConnection();
                con.setConnectTimeout(connectTimeout);
                con.setReadTimeout(readTimeout);
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "plain/text");
                con.setDoOutput(true);

                if (userName != null && password != null) {
                    String encoded = Base64.getEncoder().encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
                    con.setRequestProperty("Authorization", "Basic " + encoded);
                }

                String body = batch.stream()
                        .filter(m -> !Double.isNaN(m.value()))
                        .map(m -> {
                            String field = StreamSupport.stream(m.id().tags().spliterator(), false)
                                    .filter(t -> t.key().equals("statistic"))
                                    .findAny()
                                    .map(Tag::value)
                                    .orElse("value");

                            String tags = StreamSupport.stream(m.id().tags().spliterator(), false)
                                    .filter(t -> !t.key().equals("statistic"))
                                    .map(t -> "," + t.key() + "=" + t.value())
                                    .collect(joining(""));

                            return m.id().name() + tags + " " + field + "=" + m.value() + " " + time;
                        }).collect(joining("\n"));

                if(compressed)
                    con.setRequestProperty("Content-Encoding", "gzip");

                try (OutputStream os = con.getOutputStream();
                     GZIPOutputStream gz = new GZIPOutputStream(os)) {
                    if(compressed) {
                        gz.write(body.getBytes());
                        gz.flush();
                    }
                    else {
                        os.write(body.getBytes());
                    }
                    os.flush();
                }

                int status = con.getResponseCode();

                if (status >= 200 && status < 300) {
                    logger.info("successfully sent " + batch.size() + " metrics to influx");
                } else if (status >= 400) {
                    try (InputStream in = (status >= 400) ? con.getErrorStream() : con.getInputStream()) {
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
