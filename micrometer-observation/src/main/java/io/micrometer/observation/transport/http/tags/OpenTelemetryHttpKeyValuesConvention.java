/**
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.observation.transport.http.tags;

import io.micrometer.common.KeyValue;
import io.micrometer.conventions.common.AttributeType;
import io.micrometer.observation.transport.http.HttpRequest;
import io.micrometer.observation.transport.http.HttpResponse;

import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Conventions for HTTP key values implemented with OpenTelemetry.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
// TODO: What to do if request is not set? UNKNOWN?
abstract class OpenTelemetryHttpKeyValuesConvention implements HttpKeyValuesConvention {

    // TODO: This is just an example
    private static final Predicate<Object> METHOD_PREDICATE = s -> isTypeCorrect(
            io.micrometer.conventions.semantic.SemanticAttributes.HTTP_METHOD.getType(), s)
            && Stream.of("GET", "POST", "PATCH", "HEAD", "DELETE", "PUT")
            .anyMatch(method -> method.equalsIgnoreCase(((String) s)));

    @Override
    public KeyValue method(HttpRequest request) {
        return OpenTelemetryHttpLowCardinalityKeyNames.METHOD.of(request.method(), METHOD_PREDICATE);
    }

    @Override
    public KeyValue url(HttpRequest request) {
        return OpenTelemetryHttpLowCardinalityKeyNames.URL.of("UNKNOWN");
    }

    @Override
    public KeyValue target(HttpRequest request) {
        return OpenTelemetryHttpLowCardinalityKeyNames.TARGET.of("UNKNOWN");
    }

    @Override
    public KeyValue host(HttpRequest request) {
        return OpenTelemetryHttpLowCardinalityKeyNames.HOST.of("UNKNOWN");
    }

    @Override
    public KeyValue scheme(HttpRequest request) {
        return OpenTelemetryHttpLowCardinalityKeyNames.SCHEME.of("UNKNOWN");
    }

    @Override
    public KeyValue statusCode(HttpResponse response) {
        return OpenTelemetryHttpLowCardinalityKeyNames.STATUS_CODE.of("UNKNOWN");
    }

    @Override
    public KeyValue flavor(HttpRequest request) {
        return OpenTelemetryHttpLowCardinalityKeyNames.FLAVOR.of("UNKNOWN");
    }

    @Override
    public KeyValue userAgent(HttpRequest request) {
        return OpenTelemetryHttpLowCardinalityKeyNames.USER_AGENT.of("UNKNOWN");
    }

    @Override
    public KeyValue requestContentLength(HttpRequest request) {
        return OpenTelemetryHttpLowCardinalityKeyNames.REQUEST_CONTENT_LENGTH.of("UNKNOWN");
    }

    @Override
    public KeyValue responseContentLength(HttpResponse response) {
        return OpenTelemetryHttpLowCardinalityKeyNames.RESPONSE_CONTENT_LENGTH.of("UNKNOWN");
    }

    @Override
    public KeyValue ip(HttpRequest request) {
        return OpenTelemetryHttpLowCardinalityKeyNames.IP.of("UNKNOWN");
    }

    @Override
    public KeyValue port(HttpRequest request) {
        return OpenTelemetryHttpLowCardinalityKeyNames.PORT.of("UNKNOWN");
    }

    static boolean isTypeCorrect(AttributeType type, Object value) {
        switch (type) {
        case STRING:
            return value instanceof String;
        case BOOLEAN:
            return value instanceof Boolean;
        case LONG:
            return value instanceof Long;
        case DOUBLE:
            return value instanceof Double;
        case STRING_ARRAY:
        case BOOLEAN_ARRAY:
        case LONG_ARRAY:
        case DOUBLE_ARRAY:
        default:
            throw new UnsupportedOperationException("TODO");
        }
    }

}
