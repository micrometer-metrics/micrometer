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

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.autoconfigure.web.servlet.WebMvcMetricsConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link WebMvcMetrics}.
 *
 * @author Jon Schneider
 */
@RunWith(SpringRunner.class)
@WebAppConfiguration
public class WebMvcMetricsIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SimpleMeterRegistry registry;

    @Autowired
    private MockClock clock;

    private MockMvc mvc;

    @Autowired
    private MetricsFilter filter;

    @Before
    public void setupMockMvc() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context)
            .addFilters(filter)
            .build();
    }

    @Test
    public void handledExceptionIsRecordedInMetricTag() throws Exception {
        this.mvc.perform(get("/api/handledError")).andExpect(status().is5xxServerError());

        clock.add(SimpleConfig.DEFAULT_STEP);
        assertThat(this.registry.find("http.server.requests")
            .tags("exception", "Exception1", "status", "500").value(Statistic.Count, 1.0).timer())
            .isPresent();
    }

    @Test
    public void rethrownExceptionIsRecordedInMetricTag() throws Exception {
        assertThatCode(() -> this.mvc.perform(get("/api/rethrownError"))
            .andExpect(status().is5xxServerError()));

        clock.add(SimpleConfig.DEFAULT_STEP);
        assertThat(this.registry.find("http.server.requests")
            .tags("exception", "Exception2", "status", "500").value(Statistic.Count, 1.0).timer())
            .isPresent();
    }

    @Configuration
    @EnableWebMvc
    @Import(WebMvcMetricsConfiguration.class)
    static class TestConfiguration {
        @Bean
        MockClock clock() {
            return new MockClock();
        }

        @Bean
        MeterRegistry meterRegistry(Clock clock) {
            return new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        }

        @RestController
        @RequestMapping("/api")
        @Timed
        static class Controller1 {

            @Bean
            public CustomExceptionHandler controllerAdvice() {
                return new CustomExceptionHandler();
            }

            @GetMapping("/handledError")
            public String handledError() {
                throw new Exception1();
            }

            @GetMapping("/rethrownError")
            public String rethrownError() {
                throw new Exception2();
            }

        }
    }

    static class Exception1 extends RuntimeException {
    }

    static class Exception2 extends RuntimeException {
    }

    @ControllerAdvice
    static class CustomExceptionHandler {

        @Autowired
        WebMvcMetrics metrics;

        @ExceptionHandler
        ResponseEntity<String> handleError(Exception1 ex) throws Throwable {
            this.metrics.tagWithException(ex);
            return new ResponseEntity<>("this is a custom exception body",
                HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @ExceptionHandler
        ResponseEntity<String> rethrowError(Exception2 ex) throws Throwable {
            throw ex;
        }
    }
}
