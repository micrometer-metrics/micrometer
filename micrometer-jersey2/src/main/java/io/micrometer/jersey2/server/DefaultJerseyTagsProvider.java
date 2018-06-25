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
package io.micrometer.jersey2.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.uri.UriTemplate;

import io.micrometer.core.instrument.Tag;

/**
 * Default {@link JerseyTagsProvider} resembling the Spring MVC tag scheme.
 * 
 * @author Michael Weirauch
 */
public final class DefaultJerseyTagsProvider implements JerseyTagsProvider {

    private static final Pattern URI_CLEANUP_PATTERN = Pattern.compile("//+");
	
    // VisibleForTesting
    static final String TAG_METHOD = "method";

    // VisibleForTesting
    static final String TAG_URI = "uri";

    // VisibleForTesting
    static final String TAG_STATUS = "status";

    // VisibleForTesting
    static final String TAG_EXCEPTION = "exception";

    @Override
    public Iterable<Tag> httpRequestTags(RequestEvent event) {
        final int statusCode = statusCode(event);
        return Arrays.asList(method(event), uri(event, statusCode), status(statusCode),
                exception(event, statusCode));
    }

    @Override
    public Iterable<Tag> httpLongRequestTags(RequestEvent event) {
        return Arrays.asList(method(event), uri(event, 0));
    }

    private static Tag method(RequestEvent event) {
        final String httpMethod = event.getContainerRequest().getMethod();
        return Tag.of(TAG_METHOD, httpMethod);
    }

    private static Tag uri(RequestEvent event, int statusCode) {
        final String uri;
        if (statusCode == 404) {
            uri = "NOT_FOUND";
        } else if (isRedirect(statusCode)) {
            uri = "REDIRECTION";
        } else {
            uri = templatedUri(event);
        }
        return Tag.of(TAG_URI, uri);
    }

    private static Tag status(int statusCode) {
        return Tag.of(TAG_STATUS, Integer.valueOf(statusCode).toString());
    }

    private static Tag exception(RequestEvent event, int statusCode) {
        final Throwable exception = event.getException();
        if (exception != null && statusCode != 404 && !isRedirect(statusCode)) {
            final Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            return Tag.of(TAG_EXCEPTION, cause.getClass().getSimpleName());
        }
        return Tag.of(TAG_EXCEPTION, "None");
    }

    private static int statusCode(RequestEvent event) {
        ContainerResponse containerResponse = event.getContainerResponse();
        if (containerResponse != null) {
            return containerResponse.getStatus();
        }
        return Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
    }

    private static boolean isRedirect(int status) {
        return Family.REDIRECTION.equals(Family.familyOf(status));
    }

    private static String templatedUri(RequestEvent event) {
        final ExtendedUriInfo uriInfo = event.getUriInfo();
        final List<UriTemplate> matchedTemplates = new ArrayList<>(uriInfo.getMatchedTemplates());
        if (matchedTemplates.size() > 1) {
            Collections.reverse(matchedTemplates);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(uriInfo.getBaseUri().getPath());
        for (UriTemplate uriTemplate : matchedTemplates) {
            sb.append(uriTemplate.getTemplate());
        }
        
        return URI_CLEANUP_PATTERN.matcher(sb.toString()).replaceAll("/");
    }

}
