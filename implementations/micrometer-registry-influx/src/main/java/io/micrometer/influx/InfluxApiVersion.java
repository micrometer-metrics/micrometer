/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.influx;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import io.micrometer.core.ipc.http.HttpSender;

/**
 * @author Jakub Bednar (15/03/2021 14:08)
 */
public enum InfluxApiVersion {
    V1 {
        @Override
        String writeEndpoint(final InfluxMeterRegistry registry) {
            InfluxConfig config = registry.config;
            return config.uri() + "/write?consistency=" + config.consistency().toString().toLowerCase() + "&precision=ms&db=" + config.db();
        }

        @Override
        void addHeaderToken(final InfluxMeterRegistry registry, final HttpSender.Request.Builder requestBuilder) {
            InfluxConfig config = registry.config;
            if (config.token() != null) {
                requestBuilder.withHeader("Authorization", "Bearer " + config.token());
            }
        }
    },
    
    V2 {
        @Override
        String writeEndpoint(final InfluxMeterRegistry registry) throws UnsupportedEncodingException {
            InfluxConfig config = registry.config;
            String bucket = URLEncoder.encode(config.bucket(), "UTF-8");
            String org = URLEncoder.encode(config.org(), "UTF-8");
            return config.uri() + "/api/v2/write?&precision=ms&bucket=" + bucket + "&org=" + org;
        }

        @Override
        void addHeaderToken(final InfluxMeterRegistry registry, final HttpSender.Request.Builder requestBuilder) {
            InfluxConfig config = registry.config;
            if (config.token() != null) {
                requestBuilder.withHeader("Authorization", "Token " + config.token());
            }
        }
    };

    abstract String writeEndpoint(final InfluxMeterRegistry registry) throws UnsupportedEncodingException;

    abstract void addHeaderToken(final InfluxMeterRegistry registry, final HttpSender.Request.Builder requestBuilder);
}
