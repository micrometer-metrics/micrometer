package io.micrometer.spring.web.servlet;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

public class WebMvcTagsTest {
    @Test
    public void uriTrailingSlashesAreSuppressed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "//foo/");
        assertThat(WebMvcTags.uri(request, null).getValue()).isEqualTo("/foo");
    }

    @Test
    public void redirectsAreShunted() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(302);
        assertThat(WebMvcTags.uri(null, response).getValue()).isEqualTo("REDIRECTION");
    }

    @Test
    public void notFoundsAreShunted() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);
        assertThat(WebMvcTags.uri(null, response).getValue()).isEqualTo("NOT_FOUND");
    }
}