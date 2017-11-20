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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.NotAcceptableException;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.monitoring.RequestEventImpl;
import org.glassfish.jersey.server.internal.monitoring.RequestEventImpl.Builder;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEvent.Type;
import org.glassfish.jersey.uri.UriTemplate;
import org.junit.Test;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

/**
 * @author Michael Weirauch
 */
public class DefaultJerseyTagsProviderTest {

    private final DefaultJerseyTagsProvider uut = new DefaultJerseyTagsProvider();

    @Test
    public void testRootPath() {
        assertThat(uut.httpRequestTags(event("GET", 200, null, "/", (String[]) null)))
                .isEqualTo(tagsFrom("GET", "/", 200, null));
    }

    @Test
    public void templatedPathsAreReturned() {
        assertThat(uut.httpRequestTags(event("GET", 200, null, "/", "/", "/hello/{name}")))
                .isEqualTo(tagsFrom("GET", "/hello/{name}", 200, null));
    }

    @Test
    public void applicationPathIsPresent() {
        assertThat(uut.httpRequestTags(event("GET", 200, null, "/app", "/", "/hello")))
                .isEqualTo(tagsFrom("GET", "/app/hello", 200, null));
    }

    @Test
    public void notFoundsAreShunted() {
        assertThat(uut.httpRequestTags(event("GET", 404, null, "/app", "/", "/not-found")))
                .isEqualTo(tagsFrom("GET", "NOT_FOUND", 404, null));
    }

    @Test
    public void redirectsAreShunted() {
        assertThat(uut.httpRequestTags(event("GET", 301, null, "/app", "/", "/redirect301")))
                .isEqualTo(tagsFrom("GET", "REDIRECTION", 301, null));
        assertThat(uut.httpRequestTags(event("GET", 302, null, "/app", "/", "/redirect302")))
                .isEqualTo(tagsFrom("GET", "REDIRECTION", 302, null));
        assertThat(uut.httpRequestTags(event("GET", 399, null, "/app", "/", "/redirect399")))
                .isEqualTo(tagsFrom("GET", "REDIRECTION", 399, null));
    }

    @Test
    public void exceptionsAreMappedCorrectly() {
        assertThat(uut.httpRequestTags(
                event("GET", 500, new IllegalArgumentException(), "/app", (String[]) null)))
                        .isEqualTo(tagsFrom("GET", "/app", 500, "IllegalArgumentException"));
        assertThat(uut.httpRequestTags(event("GET", 500,
                new IllegalArgumentException(new NullPointerException()), "/app", (String[]) null)))
                        .isEqualTo(tagsFrom("GET", "/app", 500, "NullPointerException"));
        assertThat(uut.httpRequestTags(
                event("GET", 406, new NotAcceptableException(), "/app", (String[]) null)))
                        .isEqualTo(tagsFrom("GET", "/app", 406, "NotAcceptableException"));
    }

    private static RequestEvent event(String method, Integer status, Exception exception,
            String baseUri, String... uriTemplateStrings) {
        Builder builder = new RequestEventImpl.Builder();

        ContainerRequest containerRequest = mock(ContainerRequest.class);
        when(containerRequest.getMethod()).thenReturn(method);
        builder.setContainerRequest(containerRequest);

        ContainerResponse containerResponse = mock(ContainerResponse.class);
        when(containerResponse.getStatus()).thenReturn(status);
        builder.setContainerResponse(containerResponse);

        builder.setException(exception, null);

        ExtendedUriInfo extendedUriInfo = mock(ExtendedUriInfo.class);
        when(extendedUriInfo.getBaseUri()).thenReturn(
                URI.create("http://localhost:8080" + (baseUri == null ? "/" : baseUri)));
        List<UriTemplate> uriTemplates = uriTemplateStrings == null ? Collections.emptyList()
                : Arrays.stream(uriTemplateStrings).map(uri -> new UriTemplate(uri))
                        .collect(Collectors.toList());
        // UriTemplate are returned in reverse order
        Collections.reverse(uriTemplates);
        when(extendedUriInfo.getMatchedTemplates()).thenReturn(uriTemplates);
        builder.setExtendedUriInfo(extendedUriInfo);

        return builder.build(Type.FINISHED);
    }

    private static Iterable<Tag> tagsFrom(String method, String uri, int status, String exception) {
        return Tags.zip(DefaultJerseyTagsProvider.TAG_METHOD, method,
                DefaultJerseyTagsProvider.TAG_URI, uri, DefaultJerseyTagsProvider.TAG_STATUS,
                String.valueOf(status), DefaultJerseyTagsProvider.TAG_EXCEPTION,
                exception == null ? "None" : exception);
    }

}
