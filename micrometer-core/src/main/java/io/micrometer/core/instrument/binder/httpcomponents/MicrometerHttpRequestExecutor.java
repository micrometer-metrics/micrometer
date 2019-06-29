/*
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.apache.http.*;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

/**
 * This HttpRequestExecutor tracks the request duration of every request, that
 * goes through a {@link org.apache.http.client.HttpClient}. It must be
 * registered as request executor when creating the HttpClient instance.
 *
 * @author Benjamin Hubert (benjamin.hubert@willhaben.at)
 * @author Tommy Ludwig
 * @since 1.2.0
 */
@Incubating(since = "1.2.0")
public class MicrometerHttpRequestExecutor extends HttpRequestExecutor {

    private static final String METER_NAME = "httpcomponents.httpclient.request";
    public static final String DEFAULT_URI_PATTERN_HEADER = "URI_PATTERN";
    private static final String UNKNOWN = "UNKNOWN";

    private static final Tag METHOD_UNKNOWN = Tag.of("method", UNKNOWN);
    private static final Tag STATUS_UNKNOWN = Tag.of("status", UNKNOWN);
    private static final Tag STATUS_CLIENT_ERROR = Tag.of("status", "CLIENT_ERROR");
    private static final Tag STATUS_IO_ERROR = Tag.of("status", "IO_ERROR");
    private static final Tag URI_UNKNOWN = Tag.of("uri", UNKNOWN);

    private final MeterRegistry registry;
    private final Function<HttpRequest, String> uriMapper;
    private final Iterable<Tag> extraTags;
    private final boolean exportTagsForRoute;

    /**
     * Use {@link #builder(MeterRegistry)} to create an instance of this class.
     */
    private MicrometerHttpRequestExecutor(int waitForContinue,
                                          MeterRegistry registry,
                                          Function<HttpRequest, String> uriMapper,
                                          Iterable<Tag> extraTags,
                                          boolean exportTagsForRoute) {
        super(waitForContinue);
        this.registry = Optional.ofNullable(registry).orElseThrow(() -> new IllegalArgumentException("registry is required but has been initialized with null"));
        this.uriMapper = Optional.ofNullable(uriMapper).orElseThrow(() -> new IllegalArgumentException("uriMapper is required but has been initialized with null"));
        this.extraTags = Optional.ofNullable(extraTags).orElse(Collections.emptyList());
        this.exportTagsForRoute = exportTagsForRoute;
    }

    /**
     * Use this method to create an instance of {@link MicrometerHttpRequestExecutor}.
     *
     * @param registry The registry to register the metrics to.
     * @return An instance of the builder, which allows further configuration of
     * the request executor.
     */
    public static Builder builder(MeterRegistry registry) {
        return new Builder(registry);
    }

    @Override
    public HttpResponse execute(HttpRequest request, HttpClientConnection conn, HttpContext context) throws IOException, HttpException {
        Timer.Sample timerSample = Timer.start(registry);

        Tag method = request != null ? Tag.of("method", request.getRequestLine().getMethod()) : METHOD_UNKNOWN;
        Tag uri = request != null ? Tag.of("uri", uriMapper.apply(request)) : URI_UNKNOWN;
        Tag status = STATUS_UNKNOWN;

        Tags routeTags = exportTagsForRoute ? generateTagsForRoute(context) : Tags.empty();

        try {
            HttpResponse response = super.execute(request, conn, context);
            status = response != null ? Tag.of("status", Integer.toString(response.getStatusLine().getStatusCode())) : STATUS_CLIENT_ERROR;
            return response;
        } catch (IOException | HttpException | RuntimeException e) {
            status = STATUS_IO_ERROR;
            throw e;
        } finally {
            Iterable<Tag> tags = Tags.of(extraTags)
                    .and(routeTags)
                    .and(uri, method, status);

            timerSample.stop(Timer.builder(METER_NAME)
                    .description("Duration of Apache HttpClient request execution")
                    .tags(tags)
                    .register(registry));
        }
    }

    private Tags generateTagsForRoute(HttpContext context) {
        Optional<HttpHost> route = Optional.ofNullable(context)
                .map(ctx -> ctx.getAttribute("http.route"))
                .filter(o -> o instanceof HttpRoute)
                .map(o -> (HttpRoute) o)
                .map(HttpRoute::getTargetHost);
        String targetScheme = route.map(HttpHost::getSchemeName).orElse(UNKNOWN);
        String targetHost = route.map(HttpHost::getHostName).orElse(UNKNOWN);
        String targetPort = route.map(HttpHost::getPort).map(String::valueOf).orElse(UNKNOWN);
        return Tags.of(
                "target.scheme", targetScheme,
                "target.host", targetHost,
                "target.port", targetPort
        );
    }

    public static class Builder {
        private final MeterRegistry registry;
        private int waitForContinue = HttpRequestExecutor.DEFAULT_WAIT_FOR_CONTINUE;
        private Iterable<Tag> tags = Collections.emptyList();
        private Function<HttpRequest, String> uriMapper = new DefaultUriMapper();
        private boolean exportTagsForRoute = false;

        Builder(MeterRegistry registry) {
            this.registry = registry;
        }

        /**
         * @param waitForContinue Overrides the wait for continue time for this
         *                        request executor. See {@link HttpRequestExecutor}
         *                        for details.
         * @return This builder instance.
         */
        public Builder waitForContinue(int waitForContinue) {
            this.waitForContinue = waitForContinue;
            return this;
        }

        /**
         * @param tags Additional tags which should be exposed with every value.
         * @return This builder instance.
         */
        public Builder tags(Iterable<Tag> tags) {
            this.tags = tags;
            return this;
        }

        /**
         * Allows to register a mapping function for exposing request URIs. Be
         * careful, exposing request URIs could result in a huge number of tag
         * values, which could cause problems in your meter registry.
         *
         * By default, this feature is almost disabled. It only exposes values
         * of the {@link #DEFAULT_URI_PATTERN_HEADER} HTTP header.
         *
         * @param uriMapper A mapper that allows mapping and exposing request
         *                  paths. By default this feature is completely
         * @return This builder instance.
         * @see DefaultUriMapper
         */
        public Builder uriMapper(Function<HttpRequest, String> uriMapper) {
            this.uriMapper = uriMapper;
            return this;
        }

        /**
         * Allows to expose the target scheme, host and port with every metric.
         * Be careful with enabling this feature: If your client accesses a huge
         * number of remote servers, this would result in a huge number of tag
         * values, which could cause cardinality problems.
         *
         * By default, this feature is disabled.
         *
         * @param exportTagsForRoute Set this to true, if the metrics should be
         *                           tagged with the target route.
         * @return This builder instance.
         */
        public Builder exportTagsForRoute(boolean exportTagsForRoute) {
            this.exportTagsForRoute = exportTagsForRoute;
            return this;
        }

        /**
         * @return Creates an instance of {@link MicrometerHttpRequestExecutor}
         * with all the configured properties.
         */
        public MicrometerHttpRequestExecutor build() {
            return new MicrometerHttpRequestExecutor(waitForContinue, registry, uriMapper, tags, exportTagsForRoute);
        }
    }

    /**
     * Extracts the pattern from the request header of the request if available.
     */
    private static class DefaultUriMapper implements Function<HttpRequest, String> {
        @Override
        public String apply(HttpRequest httpRequest) {
            if (httpRequest != null) {
                Header uriPattern = httpRequest.getLastHeader(DEFAULT_URI_PATTERN_HEADER);
                if (uriPattern != null && uriPattern.getValue() != null) {
                    return uriPattern.getValue();
                }
            }
            return UNKNOWN;
        }
    }

}
