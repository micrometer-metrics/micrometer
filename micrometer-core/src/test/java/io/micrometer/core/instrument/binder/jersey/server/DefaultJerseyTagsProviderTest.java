/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jersey.server;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.monitoring.RequestEventImpl.Builder;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEvent.Type;
import org.glassfish.jersey.uri.UriTemplate;
import org.junit.jupiter.api.Test;

import javax.ws.rs.NotAcceptableException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultJerseyTagsProvider}.
 *
 * @author Michael Weirauch
 * @author Johnny Lim
 */
@SuppressWarnings("deprecation")
class DefaultJerseyTagsProviderTest {

    private final DefaultJerseyTagsProvider tagsProvider = new DefaultJerseyTagsProvider();

    @Test
    void testRootPath() {
        assertThat(tagsProvider.httpRequestTags(event(200, null, "/", "/")))
            .containsExactlyInAnyOrder(tagsFrom("root", 200, null, "SUCCESS"));
    }

    @Test
    void templatedPathsAreReturned() {
        assertThat(tagsProvider.httpRequestTags(event(200, null, "/", "/", "/hello/{name}")))
            .containsExactlyInAnyOrder(tagsFrom("/hello/{name}", 200, null, "SUCCESS"));
    }

    @Test
    void applicationPathIsPresent() {
        assertThat(tagsProvider.httpRequestTags(event(200, null, "/app", "/", "/hello")))
            .containsExactlyInAnyOrder(tagsFrom("/app/hello", 200, null, "SUCCESS"));
    }

    @Test
    void notFoundsAreShunted() {
        assertThat(tagsProvider.httpRequestTags(event(404, null, "/app", "/", "/not-found")))
            .containsExactlyInAnyOrder(tagsFrom("NOT_FOUND", 404, null, "CLIENT_ERROR"));
    }

    @Test
    void redirectsAreShunted() {
        assertThat(tagsProvider.httpRequestTags(event(301, null, "/app", "/", "/redirect301")))
            .containsExactlyInAnyOrder(tagsFrom("REDIRECTION", 301, null, "REDIRECTION"));
        assertThat(tagsProvider.httpRequestTags(event(302, null, "/app", "/", "/redirect302")))
            .containsExactlyInAnyOrder(tagsFrom("REDIRECTION", 302, null, "REDIRECTION"));
        assertThat(tagsProvider.httpRequestTags(event(399, null, "/app", "/", "/redirect399")))
            .containsExactlyInAnyOrder(tagsFrom("REDIRECTION", 399, null, "REDIRECTION"));
    }

    @Test
    @SuppressWarnings("serial")
    void exceptionsAreMappedCorrectly() {
        assertThat(tagsProvider.httpRequestTags(event(500, new IllegalArgumentException(), "/app", "/")))
            .containsExactlyInAnyOrder(tagsFrom("/app", 500, "IllegalArgumentException", "SERVER_ERROR"));
        assertThat(tagsProvider
            .httpRequestTags(event(500, new IllegalArgumentException(new NullPointerException()), "/app", "/")))
            .containsExactlyInAnyOrder(tagsFrom("/app", 500, "NullPointerException", "SERVER_ERROR"));
        assertThat(tagsProvider.httpRequestTags(event(406, new NotAcceptableException(), "/app", "/")))
            .containsExactlyInAnyOrder(tagsFrom("/app", 406, "NotAcceptableException", "CLIENT_ERROR"));
        assertThat(tagsProvider.httpRequestTags(event(500, new Exception("anonymous") {
        }, "/app", "/"))).containsExactlyInAnyOrder(tagsFrom("/app", 500,
                "io.micrometer.core.instrument.binder.jersey.server.DefaultJerseyTagsProviderTest$1", "SERVER_ERROR"));
    }

    @Test
    void longRequestTags() {
        assertThat(tagsProvider.httpLongRequestTags(event(0, null, "/app", "/")))
            .containsExactlyInAnyOrder(Tag.of("method", "GET"), Tag.of("uri", "/app"));
    }

    private static RequestEvent event(Integer status, Exception exception, String baseUri,
            String... uriTemplateStrings) {
        Builder builder = new Builder();

        ContainerRequest containerRequest = mock(ContainerRequest.class);
        when(containerRequest.getMethod()).thenReturn("GET");
        builder.setContainerRequest(containerRequest);

        ContainerResponse containerResponse = mock(ContainerResponse.class);
        when(containerResponse.getStatus()).thenReturn(status);
        builder.setContainerResponse(containerResponse);

        builder.setException(exception, null);

        ExtendedUriInfo extendedUriInfo = mock(ExtendedUriInfo.class);
        when(extendedUriInfo.getBaseUri())
            .thenReturn(URI.create("http://localhost:8080" + (baseUri == null ? "/" : baseUri)));
        List<UriTemplate> uriTemplates = uriTemplateStrings == null ? Collections.emptyList()
                : Arrays.stream(uriTemplateStrings).map(uri -> new UriTemplate(uri)).collect(Collectors.toList());
        // UriTemplate are returned in reverse order
        Collections.reverse(uriTemplates);
        when(extendedUriInfo.getMatchedTemplates()).thenReturn(uriTemplates);
        builder.setExtendedUriInfo(extendedUriInfo);

        return builder.build(Type.FINISHED);
    }

    private static Tag[] tagsFrom(String uri, int status, String exception, String outcome) {
        Iterable<Tag> expectedTags = Tags.of("method", "GET", "uri", uri, "status", String.valueOf(status), "exception",
                exception == null ? "None" : exception, "outcome", outcome);

        return stream(expectedTags.spliterator(), false).toArray(Tag[]::new);
    }

}
