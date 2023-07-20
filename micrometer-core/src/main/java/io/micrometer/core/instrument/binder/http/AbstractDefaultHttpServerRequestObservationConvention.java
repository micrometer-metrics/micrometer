/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.http;

import java.net.URI;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.binder.http.HttpObservationDocumentation.CommonHighCardinalityKeys;
import io.micrometer.core.instrument.binder.http.HttpObservationDocumentation.CommonLowCardinalityKeys;

class AbstractDefaultHttpServerRequestObservationConvention {

    protected static final String DEFAULT_NAME = "http.server.requests";

    private static final KeyValue METHOD_UNKNOWN = KeyValue.of(CommonLowCardinalityKeys.METHOD, "UNKNOWN");

    private static final KeyValue STATUS_UNKNOWN = KeyValue.of(CommonLowCardinalityKeys.STATUS, "UNKNOWN");

    private static final KeyValue HTTP_OUTCOME_SUCCESS = KeyValue.of(CommonLowCardinalityKeys.OUTCOME, "SUCCESS");

    private static final KeyValue HTTP_OUTCOME_UNKNOWN = KeyValue.of(CommonLowCardinalityKeys.OUTCOME, "UNKNOWN");

    private static final KeyValue URI_UNKNOWN = KeyValue.of(CommonLowCardinalityKeys.URI, "UNKNOWN");

    private static final KeyValue URI_ROOT = KeyValue.of(CommonLowCardinalityKeys.URI, "root");

    private static final KeyValue URI_NOT_FOUND = KeyValue.of(CommonLowCardinalityKeys.URI, "NOT_FOUND");

    private static final KeyValue URI_REDIRECTION = KeyValue.of(CommonLowCardinalityKeys.URI, "REDIRECTION");

    private static final KeyValue EXCEPTION_NONE = KeyValue.of(CommonLowCardinalityKeys.EXCEPTION, KeyValue.NONE_VALUE);

    private static final KeyValue HTTP_URL_UNKNOWN = KeyValue.of(CommonHighCardinalityKeys.HTTP_URL, "UNKNOWN");

    protected String getContextualName(String lowercaseHttpMethod, @Nullable String pathPattern) {
        if (pathPattern != null) {
            return "http " + lowercaseHttpMethod + " " + pathPattern;
        }
        return "http " + lowercaseHttpMethod;
    }

    protected KeyValues getLowCardinalityKeyValues(@Nullable Throwable error, @Nullable String method,
            @Nullable Integer status, @Nullable String pathPattern, @Nullable String requestUri) {
        // Make sure that KeyValues entries are already sorted by name for better
        // performance
        KeyValues micrometerKeyValues = KeyValues.of(exception(error), method(method), outcome(status), status(status),
                uri(pathPattern, status));
        if (method == null) {
            return micrometerKeyValues;
        }
        return micrometerKeyValues.and(lowCardinalityKeyValues(method, requestUri, status));
    }

    private KeyValues lowCardinalityKeyValues(String method, String requestUri, @Nullable Integer responseStatus) {
        try {
            URI uri = URI.create(requestUri);
            KeyValue requestMethod = CommonLowCardinalityKeys.HTTP_REQUEST_METHOD.withValue(method);
            KeyValue network = CommonLowCardinalityKeys.NETWORK_PROTOCOL_NAME.withValue("http");
            KeyValue serverAddress = CommonLowCardinalityKeys.SERVER_ADDRESS.withValue(uri.getHost());
            KeyValue serverPort = CommonLowCardinalityKeys.SERVER_PORT.withValue(String.valueOf(uri.getPort()));
            KeyValue urlScheme = CommonLowCardinalityKeys.URL_SCHEME.withValue(String.valueOf(uri.getScheme()));
            KeyValues keyValues = KeyValues.of(requestMethod, network, serverAddress, serverPort, urlScheme);
            if (responseStatus != null) {
                keyValues = keyValues
                    .and(CommonLowCardinalityKeys.HTTP_RESPONSE_STATUS_CODE.withValue(String.valueOf(responseStatus)));
            }
            return keyValues;
        }
        catch (Exception ex) {
            return KeyValues.empty();
        }
    }

    protected KeyValues getHighCardinalityKeyValues(String requestUri) {
        return KeyValues.of(httpUrl(requestUri));
    }

    protected KeyValue method(@Nullable String method) {
        return (method != null) ? KeyValue.of(CommonLowCardinalityKeys.METHOD, method) : METHOD_UNKNOWN;
    }

    private KeyValue status(@Nullable Integer status) {
        return (status != null) ? KeyValue.of(CommonLowCardinalityKeys.STATUS, Integer.toString(status))
                : STATUS_UNKNOWN;
    }

    private KeyValue uri(@Nullable String pattern, @Nullable Integer status) {
        if (pattern != null) {
            if (pattern.isEmpty()) {
                return URI_ROOT;
            }
            return KeyValue.of(CommonLowCardinalityKeys.URI, pattern);
        }
        if (status != null) {
            if (status >= 300 && status < 400) {
                return URI_REDIRECTION;
            }
            if (status == 404) {
                return URI_NOT_FOUND;
            }
        }
        return URI_UNKNOWN;
    }

    private KeyValue exception(@Nullable Throwable error) {
        if (error != null) {
            String simpleName = error.getClass().getSimpleName();
            return KeyValue.of(CommonLowCardinalityKeys.EXCEPTION,
                    StringUtils.isNotBlank(simpleName) ? simpleName : error.getClass().getName());
        }
        return EXCEPTION_NONE;
    }

    private KeyValue outcome(@Nullable Integer status) {
        if (status != null) {
            return HttpOutcome.forStatus(status);
        }
        return HTTP_OUTCOME_UNKNOWN;
    }

    private KeyValue httpUrl(@Nullable String requestUri) {
        if (requestUri != null) {
            return KeyValue.of(CommonHighCardinalityKeys.HTTP_URL, requestUri);
        }
        return HTTP_URL_UNKNOWN;
    }

    static class HttpOutcome {

        static KeyValue forStatus(int statusCode) {
            if (statusCode >= 200 && statusCode < 300) {
                return HTTP_OUTCOME_SUCCESS;
            }
            else {
                return HTTP_OUTCOME_UNKNOWN;
            }
        }

    }

}
