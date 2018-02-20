/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.web.client;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.lang.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;

/**
 * Factory methods for creating {@link Tag Tags} related to a request-response exchange
 * performed by a {@link RestTemplate}.
 *
 * @author Andy Wilkinson
 * @author Jon Schneider
 */
public final class RestTemplateExchangeTags {

    private RestTemplateExchangeTags() {
    }

    /**
     * Creates a {@code method} {@code Tag} for the {@link HttpRequest#getMethod() method}
     * of the given {@code request}.
     *
     * @param request the request
     * @return the method tag
     */
    public static Tag method(HttpRequest request) {
        return Tag.of("method", request.getMethod().name());
    }

    /**
     * Creates a {@code uri} {@code Tag} for the URI of the given {@code request}.
     *
     * @param request the request
     * @return the uri tag
     */
    public static Tag uri(HttpRequest request) {
        return Tag.of("uri", ensureLeadingSlash(stripUri(request.getURI().toString())));
    }

    /**
     * Creates a {@code uri} {@code Tag} from the given {@code uriTemplate}.
     *
     * @param uriTemplate the template
     * @return the uri tag
     */
    public static Tag uri(String uriTemplate) {
        String uri = StringUtils.hasText(uriTemplate) ? uriTemplate : "none";
        return Tag.of("uri", ensureLeadingSlash(stripUri(uri)));
    }

    private static String stripUri(String uri) {
        return uri.replaceAll("^https?://[^/]+/", "");
    }

    // This normalization improves tag value matching when one code path requests test/{id} and another
    // requests /test/{id}
    private static String ensureLeadingSlash(@Nullable String uri) {
        if (uri == null)
            return "/";
        return uri.startsWith("/") ? uri : "/" + uri;
    }

    /**
     * Creates a {@code status} {@code Tag} derived from the
     * {@link ClientHttpResponse#getRawStatusCode() status} of the given {@code response}.
     *
     * @param response the response
     * @return the status tag
     */
    public static Tag status(@Nullable ClientHttpResponse response) {
        return Tag.of("status", getStatusMessage(response));
    }

    private static String getStatusMessage(@Nullable ClientHttpResponse response) {
        try {
            if (response == null) {
                return "CLIENT_ERROR";
            }
            return String.valueOf(response.getRawStatusCode());
        } catch (IOException ex) {
            return "IO_ERROR";
        }
    }

    /**
     * Create a {@code clientName} {@code Tag} derived from the {@link URI#getHost host}
     * of the {@link HttpRequest#getURI() URI} of the given {@code request}.
     *
     * @param request the request
     * @return the clientName tag
     */
    public static Tag clientName(HttpRequest request) {
        String host = request.getURI().getHost();
        if (host == null) {
            host = "none";
        }
        return Tag.of("clientName", host);
    }

}
