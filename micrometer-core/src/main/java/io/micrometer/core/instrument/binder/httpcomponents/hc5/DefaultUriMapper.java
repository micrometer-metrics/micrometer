/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents.hc5;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;

import java.util.function.Function;

/**
 * Extracts the URI pattern from the predefined request header,
 * {@value DefaultUriMapper#URI_PATTERN_HEADER} if available.
 *
 * @author Benjamin Hubert
 * @since 1.11.0
 * @deprecated as of 1.12.0 in favor of an
 * {@link org.apache.hc.client5.http.protocol.HttpClientContext} value with
 * {@link ApacheHttpClientObservationConvention#URI_TEMPLATE_ATTRIBUTE} as key name.
 */
@Deprecated
public class DefaultUriMapper implements Function<HttpRequest, String> {

    /**
     * Header name for URI pattern.
     */
    public static final String URI_PATTERN_HEADER = "URI_PATTERN";

    @Override
    public String apply(HttpRequest httpRequest) {
        Header uriPattern = httpRequest.getLastHeader(URI_PATTERN_HEADER);
        if (uriPattern != null && uriPattern.getValue() != null) {
            return uriPattern.getValue();
        }
        return "UNKNOWN";
    }

}
