/*
 * Copyright 2021 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.influx;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import io.micrometer.common.util.StringUtils;
import io.micrometer.core.ipc.http.HttpSender;

/**
 * Enum for the version of the InfluxDB API.
 *
 * @author Jakub Bednar
 * @since 1.7.0
 */
public enum InfluxApiVersion {
    V1 {
        @Override
        String writeEndpoint(final InfluxConfig config) {
            String influxEndpoint = config.uri() + "/write?consistency=" + config.consistency().name().toLowerCase() + "&precision=ms&db=" + config.db();
            if (StringUtils.isNotBlank(config.retentionPolicy())) {
                influxEndpoint += "&rp=" + config.retentionPolicy();
            }
            return influxEndpoint;
        }

        @Override
        void addHeaderToken(final InfluxConfig config, final HttpSender.Request.Builder requestBuilder) {
            if (config.token() != null) {
                requestBuilder.withHeader("Authorization", "Bearer " + config.token());
            }
        }
    },
    
    V2 {
        @Override
        String writeEndpoint(final InfluxConfig config) throws UnsupportedEncodingException {
            String bucket = URLEncoder.encode(config.bucket(), "UTF-8");
            String org = URLEncoder.encode(config.org(), "UTF-8");
            return config.uri() + "/api/v2/write?precision=ms&bucket=" + bucket + "&org=" + org;
        }

        @Override
        void addHeaderToken(final InfluxConfig config, final HttpSender.Request.Builder requestBuilder) {
            if (config.token() != null) {
                requestBuilder.withHeader("Authorization", "Token " + config.token());
            }
        }
    };

    abstract String writeEndpoint(final InfluxConfig config) throws UnsupportedEncodingException;

    abstract void addHeaderToken(final InfluxConfig config, final HttpSender.Request.Builder requestBuilder);
}
