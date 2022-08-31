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
     * @return name of the meter timing http client requests
     */
    protected String timerName() {
        return "http.server.requests";
    }

    /**
     * Start the instrumented HTTP server to be tested. No request body or query
     * parameters will be sent, and any response body will be ignored. The server MUST
     * serve the following routes:
     * <ul>
     * <li>{@code GET /}</li>
     * <li>{@code GET /hello/{name}} (templated path parameter)</li>
     * <li>{@code GET /foundRedirect} (redirects to the root {@code /} with status code
     * 302)</li>
     * <li>{@code POST /error} (500 error response)</li>
     * </ul>
     * The server MUST NOT have a route for the following:
     * <ul>
     * <li>{@code GET /notFound} (returns 404 response)</li>
     * </ul>
     * @return base URI where the instrumented server is receiving requests. Must end with
     * a slash (/).
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
        checkTimer(rs -> rs.tags("uri", "/hello/{name}", "status", "200", "method", "GET").timer().count() == 1);
    }

    @Test
    void redirect() throws Throwable {
        sender.get(baseUri + "foundRedirect").send();
        checkTimer(rs -> rs.tags("uri", "/foundRedirect", "status", "302", "method", "GET").timer().count() == 1);
    }

    @Test
    void errorResponse() throws Throwable {
        sender.post(baseUri + "error").send();
        checkTimer(rs -> rs.tags("uri", "/error", "status", "500", "method", "POST").timer().count() == 1);
    }

    private void checkTimer(Function<RequiredSearch, Boolean> timerCheck) {
        // jersey instrumentation finishes after response is sent, creating a race
        await().atLeast(Duration.ofMillis(25)).atMost(Duration.ofMillis(150))
                .until(() -> timerCheck.apply(getRegistry().get(timerName())));
    }

}
