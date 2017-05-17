/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.web;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.metrics.boot.EnableMetrics;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.annotation.Timed;
import org.springframework.metrics.instrument.simple.SimpleTimer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = WebmvcMetricsHandlerInterceptorTest.App.class)
@WebMvcTest({WebmvcMetricsHandlerInterceptorTest.Controller1.class, WebmvcMetricsHandlerInterceptorTest.Controller2.class})
class WebmvcMetricsHandlerInterceptorTest {
    @Autowired
    private
    MockMvc mvc;

    @MockBean
    private
    MeterRegistry registry;

    @Test
    void metricsGatheredWhenMethodIsTimed() throws Exception {
        SimpleTimer timer = expectTimer();
        mvc.perform(get("/api/c1/10")).andExpect(status().isOk());
        assertTags(
                Tag.of("status", "200"), Tag.of("uri", "api_c1_-id-"), // default tags provided by WebMetricsTagProvider
                Tag.of("public", "true") // extra tags provided via @Timed
        );
        assertThat(timer.count()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void metricsNotGatheredWhenRequestMappingIsNotTimed() throws Exception {
        mvc.perform(get("/api/c1/untimed/10")).andExpect(status().isOk());
        verify(registry, never()).timer(anyString(), any(Stream.class));
    }

    @Test
    void metricsGatheredWhenControllerIsTimed() throws Exception {
        SimpleTimer timer = expectTimer();
        mvc.perform(get("/api/c2/10")).andExpect(status().isOk());
        assertTags(Tag.of("status", "200"));
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void metricsGatheredWhenClientRequestBad() throws Exception {
        SimpleTimer timer = expectTimer();
        mvc.perform(get("/api/c1/oops")).andExpect(status().is4xxClientError());
        assertTags(Tag.of("status", "400"), Tag.of("uri", "api_c1_-id-"));
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void metricsGatheredWhenUnhandledError() throws Exception {
        SimpleTimer timer = expectTimer();
        try {
            mvc.perform(get("/api/c1/unhandledError/10")).andExpect(status().isOk());
        } catch (Exception e) {
        }
        assertTags(Tag.of("exception", "RuntimeException"), Tag.of("status", "200"), Tag.of("uri", "api_c1_unhandledError_-id-"));
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    /* FIXME */ @Disabled("ErrorMvcAutoConfiguration is blowing up on SPEL evaluation of 'timestamp'")
    void metricsGatheredWhenHandledError() throws Exception {
        SimpleTimer timer = expectTimer();
        mvc.perform(get("/api/c1/error/10")).andExpect(status().is4xxClientError());
        assertTags(Tag.of("status", "422"), Tag.of("uri", "api_c1_error_-id-"));
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void metricsGatheredWhenRegexEndpoint() throws Exception {
        SimpleTimer timer = expectTimer();
        mvc.perform(get("/api/c1/regex/.abc")).andExpect(status().isOk());
        assertTags(Tag.of("status", "200"), Tag.of("uri", "api_c1_regex_-id-"));
        assertThat(timer.count()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private void assertTags(Tag... match) {
        ArgumentCaptor<Stream> tags = ArgumentCaptor.forClass(Stream.class);
        verify(registry).timer(anyString(), tags.capture());
        assertThat((List) tags.getValue().collect(Collectors.toList())).contains((Object[]) match);
    }

    private SimpleTimer expectTimer() {
        SimpleTimer timer = new SimpleTimer("http_server_requests");

        //noinspection unchecked
        when(registry.timer(eq("http_server_requests"), any(Stream.class))).thenReturn(timer);
        return timer;
    }

    @SpringBootApplication
    @EnableMetrics
    static class App {}

    @RestController
    @RequestMapping("/api/c1")
    static class Controller1 {
        @Timed(extraTags = {"public", "true"})
        @GetMapping("/{id}")
        public String successfulWithExtraTags(@PathVariable Long id) {
            return id.toString();
        }

        @GetMapping("/untimed/{id}")
        public String successfulButUntimed(@PathVariable Long id) {
            return id.toString();
        }

        @Timed
        @GetMapping("/error/{id}")
        public String alwaysThrowsException(@PathVariable Long id) {
            throw new IllegalStateException("Boom on $id!");
        }

        @Timed
        @GetMapping("/unhandledError/{id}")
        public String alwaysThrowsUnhandledException(@PathVariable Long id) {
            throw new RuntimeException("Boom on $id!");
        }

        @Timed
        @GetMapping("/regex/{id:\\.[a-z]+}")
        public String successfulRegex(@PathVariable String id) {
            return id;
        }

        @ExceptionHandler(value = IllegalStateException.class)
        @ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY)
        ModelAndView defaultErrorHandler(HttpServletRequest request, Exception e) {
            return new ModelAndView("error");
        }
    }

    @RestController
    @Timed
    @RequestMapping("/api/c2")
    static class Controller2 {
        @GetMapping("/{id}")
        public String successful(@PathVariable Long id) {
            return id.toString();
        }
    }
}