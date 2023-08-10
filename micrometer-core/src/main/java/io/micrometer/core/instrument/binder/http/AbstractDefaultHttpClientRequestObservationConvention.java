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
import java.util.regex.Pattern;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.binder.http.HttpObservationDocumentation.ClientLowCardinalityKeys;
import io.micrometer.core.instrument.binder.http.HttpObservationDocumentation.CommonHighCardinalityKeys;
import io.micrometer.core.instrument.binder.http.HttpObservationDocumentation.CommonLowCardinalityKeys;

class AbstractDefaultHttpClientRequestObservationConvention {

    private static final Pattern PATTERN_BEFORE_PATH = Pattern.compile("^https?://[^/]+/");

    private static final KeyValue URI_NONE = KeyValue.of(CommonLowCardinalityKeys.URI, KeyValue.NONE_VALUE);

    private static final KeyValue METHOD_NONE = KeyValue.of(CommonLowCardinalityKeys.METHOD, KeyValue.NONE_VALUE);

    private static final KeyValue STATUS_CLIENT_ERROR = KeyValue.of(CommonLowCardinalityKeys.STATUS, "CLIENT_ERROR");

    private static final KeyValue CLIENT_NAME_NONE = KeyValue.of(ClientLowCardinalityKeys.CLIENT_NAME,
            KeyValue.NONE_VALUE);

    private static final KeyValue EXCEPTION_NONE = KeyValue.of(CommonLowCardinalityKeys.EXCEPTION, KeyValue.NONE_VALUE);

    private static final KeyValue HTTP_URL_NONE = KeyValue.of(CommonHighCardinalityKeys.HTTP_URL, KeyValue.NONE_VALUE);

    private static final KeyValue USER_AGENT_NONE = KeyValue.of(CommonHighCardinalityKeys.USER_AGENT_ORIGINAL,
            KeyValue.NONE_VALUE);

    protected String getContextualName(String lowercaseMethod) {
        return "http " + lowercaseMethod;
    }

    protected KeyValues getLowCardinalityKeyValues(@Nullable URI uri, @Nullable Throwable throwable,
            @Nullable String methodName, @Nullable Integer statusCode, @Nullable String uriPathPattern) {
        return KeyValues.of(clientName(uri), exception(throwable), method(methodName), outcome(statusCode),
                status(statusCode), uri(uriPathPattern));
    }

    private KeyValue uri(@Nullable String uriTemplate) {
        if (uriTemplate != null) {
            return KeyValue.of(CommonLowCardinalityKeys.URI, extractPath(uriTemplate));
        }
        return URI_NONE;
    }

    private static String extractPath(String uriTemplate) {
        String path = PATTERN_BEFORE_PATH.matcher(uriTemplate).replaceFirst("");
        return (path.startsWith("/") ? path : "/" + path);
    }

    private KeyValue method(@Nullable String methodName) {
        if (methodName != null) {
            return KeyValue.of(CommonLowCardinalityKeys.METHOD, methodName);
        }
        else {
            return METHOD_NONE;
        }
    }

    private KeyValue status(@Nullable Integer statusCode) {
        if (statusCode == null) {
            return STATUS_CLIENT_ERROR;
        }
        return KeyValue.of(CommonLowCardinalityKeys.STATUS, String.valueOf(statusCode));
    }

    private KeyValue clientName(@Nullable URI uri) {
        if (uri != null && uri.getHost() != null) {
            return KeyValue.of(ClientLowCardinalityKeys.CLIENT_NAME, uri.getHost());
        }
        return CLIENT_NAME_NONE;
    }

    private KeyValue exception(@Nullable Throwable error) {
        if (error != null) {
            String simpleName = error.getClass().getSimpleName();
            return KeyValue.of(CommonLowCardinalityKeys.EXCEPTION,
                    StringUtils.isNotBlank(simpleName) ? simpleName : error.getClass().getName());
        }
        return EXCEPTION_NONE;
    }

    private KeyValue outcome(@Nullable Integer statusCode) {
        if (statusCode != null) {
            return HttpOutcome.forStatus(false, statusCode);
        }
        return HttpOutcome.forStatus(false, statusCode);
    }

    protected KeyValues getHighCardinalityKeyValues(@Nullable URI uri, @Nullable String userAgent) {
        // Make sure that KeyValues entries are already sorted by name for better
        // performance
        return KeyValues.of(requestUri(uri), userAgent(userAgent));
    }

    private KeyValue requestUri(@Nullable URI uri) {
        if (uri != null) {
            return KeyValue.of(CommonHighCardinalityKeys.HTTP_URL, uri.toASCIIString());
        }
        return HTTP_URL_NONE;
    }

    private KeyValue userAgent(@Nullable String userAgent) {
        if (userAgent != null) {
            return KeyValue.of(CommonHighCardinalityKeys.USER_AGENT_ORIGINAL, userAgent);
        }
        return USER_AGENT_NONE;
    }

}
