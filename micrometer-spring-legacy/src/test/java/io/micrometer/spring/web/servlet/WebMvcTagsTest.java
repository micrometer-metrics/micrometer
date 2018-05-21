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
package io.micrometer.spring.web.servlet;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

public class WebMvcTagsTest {
    private MockHttpServletRequest request = new MockHttpServletRequest();
    private MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    public void uriTrailingSlashesAreSuppressed() {
        request.setPathInfo("//foo/");
        assertThat(WebMvcTags.uri(request, null).getValue()).isEqualTo("/foo");
    }

    @Test
    public void uriTagValueIsBestMatchingPatternWhenAvailable() {
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/spring");
        response.setStatus(301);
        assertThat(WebMvcTags.uri(request, response).getValue()).isEqualTo("/spring");
    }

    @Test
    public void redirectsAreShunted() {
        response.setStatus(302);
        assertThat(WebMvcTags.uri(request, response).getValue()).isEqualTo("REDIRECTION");
    }

    @Test
    public void notFoundsAreShunted() {
        response.setStatus(404);
        assertThat(WebMvcTags.uri(request, response).getValue()).isEqualTo("NOT_FOUND");
    }
}