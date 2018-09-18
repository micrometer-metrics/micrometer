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

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.monitoring.RequestEventImpl;
import org.glassfish.jersey.server.internal.monitoring.RequestEventImpl.Builder;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEvent.Type;
import org.glassfish.jersey.uri.UriTemplate;
import org.junit.Test;

import javax.ws.rs.NotAcceptableException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.micrometer.jersey2.server.DefaultJerseyTagsProvider.*;
import static java.util.stream.StreamSupport.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Michael Weirauch
 */
public class DefaultJerseyTagsProviderTest {

    private final DefaultJerseyTagsProvider uut = new DefaultJerseyTagsProvider();

    @Test
    public void testRootPath() {
        assertThat(uut.httpRequestTags(event(200, null, "/", (String[]) null)))
            .containsExactlyInAnyOrder(tagsFrom("/", 200, null));
    }

    @Test
    public void templatedPathsAreReturned() {
        assertThat(uut.httpRequestTags(event(200, null, "/", "/", "/hello/{name}")))
            .containsExactlyInAnyOrder(tagsFrom("/hello/{name}", 200, null));
    }

    @Test
    public void applicationPathIsPresent() {
        assertThat(uut.httpRequestTags(event(200, null, "/app", "/", "/hello")))
            .containsExactlyInAnyOrder(tagsFrom("/app/hello", 200, null));
    }

    @Test
    public void notFoundsAreShunted() {
        assertThat(uut.httpRequestTags(event(404, null, "/app", "/", "/not-found")))
            .containsExactlyInAnyOrder(tagsFrom("NOT_FOUND", 404, null));
    }

    @Test
    public void redirectsAreShunted() {
        assertThat(uut.httpRequestTags(event(301, null, "/app", "/", "/redirect301")))
            .containsExactlyInAnyOrder(tagsFrom("REDIRECTION", 301, null));
        assertThat(uut.httpRequestTags(event(302, null, "/app", "/", "/redirect302")))
            .containsExactlyInAnyOrder(tagsFrom("REDIRECTION", 302, null));
        assertThat(uut.httpRequestTags(event(399, null, "/app", "/", "/redirect399")))
            .containsExactlyInAnyOrder(tagsFrom("REDIRECTION", 399, null));
    }

    @Test
    @SuppressWarnings("serial")
    public void exceptionsAreMappedCorrectly() {
        assertThat(uut.httpRequestTags(
            event(500, new IllegalArgumentException(), "/app", (String[]) null)))
            .containsExactlyInAnyOrder(tagsFrom("/app", 500, "IllegalArgumentException"));
        assertThat(uut.httpRequestTags(event(500,
            new IllegalArgumentException(new NullPointerException()), "/app", (String[]) null)))
            .containsExactlyInAnyOrder(tagsFrom("/app", 500, "NullPointerException"));
        assertThat(uut.httpRequestTags(
            event(406, new NotAcceptableException(), "/app", (String[]) null)))
            .containsExactlyInAnyOrder(tagsFrom("/app", 406, "NotAcceptableException"));
        assertThat(uut.httpRequestTags(
            event(500, new Exception("anonymous") { }, "/app", (String[]) null)))
            .containsExactlyInAnyOrder(tagsFrom("/app", 500, "io.micrometer.jersey2.server.DefaultJerseyTagsProviderTest$1"));
    }

    @Test
    public void longRequestTags() {
        assertThat(uut.httpLongRequestTags(event(0, null, "/app", (String[]) null)))
            .containsExactlyInAnyOrder(Tag.of(TAG_METHOD, "GET"), Tag.of(TAG_URI, "/app"));
    }

    private static RequestEvent event(Integer status, Exception exception, String baseUri, String... uriTemplateStrings) {
        Builder builder = new RequestEventImpl.Builder();

        ContainerRequest containerRequest = mock(ContainerRequest.class);
        when(containerRequest.getMethod()).thenReturn("GET");
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

    private static Tag[] tagsFrom(String uri, int status, String exception) {
        Iterable<Tag> expectedTags = Tags.of(
            TAG_METHOD, "GET",
            TAG_URI, uri,
            TAG_STATUS, String.valueOf(status),
            TAG_EXCEPTION, exception == null ? "None" : exception
        );

        return stream(expectedTags.spliterator(), false)
            .toArray(Tag[]::new);
    }
}
