package io.micrometer.spring.web.client;

import io.micrometer.core.instrument.Tag;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class DefaultRestTemplateExchangeTagsProviderTest {

    private DefaultRestTemplateExchangeTagsProvider tagsProvider = new DefaultRestTemplateExchangeTagsProvider();

    private MockClientHttpRequest httpRequest = new MockClientHttpRequest();
    private ClientHttpResponse httpResponse = new MockClientHttpResponse(new byte[]{}, HttpStatus.OK);

    @Before
    public void before() throws URISyntaxException {
        httpRequest.setMethod(HttpMethod.GET);
        httpRequest.setURI(new URI("http://localhost/test/123"));
    }

    @Test
    public void uriTagSetFromUriTemplate() {
        Iterable<Tag> tags = tagsProvider.getTags("/test/{id}", httpRequest, httpResponse);

        assertThat(tags).contains(Tag.of("uri", "/test/{id}"));
    }

    @Test
    public void uriTagSetFromRequestWhenNoUriTemplate() {
        Iterable<Tag> tags = tagsProvider.getTags(null, httpRequest, httpResponse);

        assertThat(tags).contains(Tag.of("uri", "/test/123"));
    }

    @Test
    public void uriTagSetFromRequestWhenUriTemplateIsBlank() {
        Iterable<Tag> tags = tagsProvider.getTags(" ", httpRequest, httpResponse);

        assertThat(tags).contains(Tag.of("uri", "/test/123"));
    }
}
