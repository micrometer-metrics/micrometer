/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.function.Function;

import static org.awaitility.Awaitility.await;

/**
 * Verify the instrumentation of an HTTP server has the minimum expected results. See
 * {@link #startInstrumentedServer()} for the required specification the instrumented HTTP
 * server must handle.
 */
@Incubating(since = "1.8.9")
public abstract class HttpServerTimingInstrumentationVerificationTests extends InstrumentationVerificationTests {

    private final HttpSender sender = new HttpUrlConnectionSender();

    private URI baseUri;

    /**
     * A default is provided that should be preferred by new instrumentations. Existing
     * instrumentations that use a different value to maintain backwards compatibility may
     * override this method to run tests with a different name used in assertions.
     * @return name of the meter timing http server requests
     */
    protected String timerName() {
        return "http.server.requests";
    }

    /**
     * Start the instrumented HTTP server to be tested. No request body or query
     * parameters will be sent, and any response body will be ignored. The server MUST
     * serve the routes described by {@link InstrumentedRoutes}. Constants are available
     * in that class for the routes that will have requests sent to them as part of the
     * TCK. The server MUST NOT have a route for the following:
     * <ul>
     * <li>{@code GET /notFound} (returns 404 response)</li>
     * </ul>
     * @return base URI where the instrumented server is receiving requests. Must end with
     * a slash (/).
     * @see InstrumentedRoutes
     */
    protected abstract URI startInstrumentedServer() throws Exception;

    /**
     * Stop the instrumented server that was started with
     * {@link #startInstrumentedServer()}.
     */
    protected abstract void stopInstrumentedServer() throws Exception;

    @BeforeEach
    void beforeEach() throws Exception {
        baseUri = startInstrumentedServer();
    }

    @AfterEach
    void afterEach() throws Exception {
        stopInstrumentedServer();
    }

    @Test
    void uriIsNotFound_whenRouteIsUnmapped() throws Throwable {
        sender.get(baseUri + "notFound").send();
        checkTimer(rs -> rs.tags("uri", "NOT_FOUND", "status", "404", "method", "GET").timer().count() == 1);
    }

    @Test
    void uriTemplateIsTagged() throws Throwable {
        sender.get(baseUri + "hello/world").send();
        checkTimer(rs -> rs.tags("uri", InstrumentedRoutes.TEMPLATED_ROUTE, "status", "200", "method", "GET")
            .timer()
            .count() == 1);
    }

    @Test
    void redirect() throws Throwable {
        sender.get(baseUri + "foundRedirect").send();
        checkTimer(rs -> rs.tags("uri", InstrumentedRoutes.REDIRECT, "status", "302", "method", "GET")
            .timer()
            .count() == 1);
    }

    @Test
    void errorResponse() throws Throwable {
        sender.post(baseUri + "error").send();
        checkTimer(
                rs -> rs.tags("uri", InstrumentedRoutes.ERROR, "status", "500", "method", "POST").timer().count() == 1);
    }

    private void checkTimer(Function<RequiredSearch, Boolean> timerCheck) {
        // jersey instrumentation finishes after response is sent, creating a race
        await().atLeast(Duration.ofMillis(25))
            .atMost(Duration.ofMillis(150))
            .until(() -> timerCheck.apply(getRegistry().get(timerName())));
    }

    /**
     * Class containing constants that can be used for implementing the HTTP routes that
     * the instrumented server needs to serve to pass the
     * {@link HttpServerTimingInstrumentationVerificationTests}. The HTTP server MUST
     * serve the following:
     * <ul>
     * <li>{@link #ROOT}: {@code GET /}</li>
     * <li>{@link #TEMPLATED_ROUTE}: {@code GET /hello/{name}}</li>
     * <li>{@link #REDIRECT}: {@code GET /foundRedirect}</li>
     * <li>{@link #ERROR}: {@code POST /error}</li>
     * </ul>
     */
    public static class InstrumentedRoutes {

        /**
         * Path for the route with a path variable. The templated route is expected to be
         * used in the URI tag.
         */
        public static final String TEMPLATED_ROUTE = "/hello/{name}";

        /**
         * Path for the root route.
         */
        public static final String ROOT = "/";

        /**
         * Path for the route that will respond with a 500 server error to a POST method
         * request.
         */
        public static final String ERROR = "/error";

        /**
         * Path for the route that will redirect with status code 302 to the {@link #ROOT}
         * route.
         */
        public static final String REDIRECT = "/foundRedirect";

    }

}
