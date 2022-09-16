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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.observation.transport.RequestReplySenderContext;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;

import java.util.Objects;
import java.util.function.Function;

/**
 * Context to use when instrumenting Jetty client metrics with the Observation API.
 *
 * @since 1.10.0
 * @see JettyClientMetrics
 */
public class JettyClientContext extends RequestReplySenderContext<Request, Result> {

    private final Function<Result, String> uriPatternFunction;

    public JettyClientContext(Request request, Function<Result, String> uriPatternFunction) {
        super((carrier, key, value) -> Objects.requireNonNull(carrier).header(key, value));
        this.uriPatternFunction = uriPatternFunction;
        setCarrier(request);
    }

    public Function<Result, String> getUriPatternFunction() {
        return uriPatternFunction;
    }

}
