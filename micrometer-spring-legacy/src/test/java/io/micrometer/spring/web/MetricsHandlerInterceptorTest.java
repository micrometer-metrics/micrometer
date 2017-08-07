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
package io.micrometer.spring.web;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.EnableMetrics;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MetricsHandlerInterceptorTest.App.class)
@WebMvcTest({MetricsHandlerInterceptorTest.Controller1.class, MetricsHandlerInterceptorTest.Controller2.class})
public class MetricsHandlerInterceptorTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private SimpleMeterRegistry registry;

    static CountDownLatch longRequestCountDown = new CountDownLatch(1);

    @After
    public void clearRegistry() {
        registry.clear();
    }

    @Test
    public void timedMethod() throws Exception {
        mvc.perform(get("/api/c1/10")).andExpect(status().isOk());
        assertThat(registry.findMeter(Timer.class, "http_server_requests", "status", "200", "uri", "/api/c1/{id}", "public", "true"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void untimedMethod() throws Exception {
        mvc.perform(get("/api/c1/untimed/10")).andExpect(status().isOk());
        assertThat(registry.findMeter(Timer.class, "http_server_requests")).isEmpty();
    }

    @Test
    public void timedControllerClass() throws Exception {
        mvc.perform(get("/api/c2/10")).andExpect(status().isOk());
        assertThat(registry.findMeter(Timer.class, "http_server_requests", "status", "200"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @Test
    public void badClientRequest() throws Exception {
        mvc.perform(get("/api/c1/oops")).andExpect(status().is4xxClientError());
        assertThat(registry.findMeter(Timer.class, "http_server_requests", "status", "400"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @Test
    public void unhandledError() throws Exception {
        assertThatCode(() -> mvc.perform(get("/api/c1/unhandledError/10")).andExpect(status().isOk()))
                .hasCauseInstanceOf(RuntimeException.class);
        assertThat(registry.findMeter(Timer.class, "http_server_requests", "exception", "RuntimeException"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @Test
    public void longRunningRequest() throws Exception {
        MvcResult result = mvc.perform(get("/api/c1/long/10"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // while the mapping is running, it contributes to the activeTasks count
        assertThat(registry.findMeter(LongTaskTimer.class, "my_long_request", "region", "test"))
                .hasValueSatisfying(t -> assertThat(t.activeTasks()).isEqualTo(1));

        // once the mapping completes, we can gather information about status, etc.
        longRequestCountDown.countDown();

        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        assertThat(registry.findMeter(Timer.class, "http_server_requests", "status", "200"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @Test
    /* FIXME */
    @Ignore("ErrorMvcAutoConfiguration is blowing up on SPEL evaluation of 'timestamp'")
    public void endpointThrowsError() throws Exception {
        mvc.perform(get("/api/c1/error/10")).andExpect(status().is4xxClientError());
        assertThat(registry.findMeter(Timer.class, "http_server_requests", "status", "422"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @Test
    public void regexBasedRequestMapping() throws Exception {
        mvc.perform(get("/api/c1/regex/.abc")).andExpect(status().isOk());
        assertThat(registry.findMeter(Timer.class, "http_server_requests", "uri", "/api/c1/regex/{id:\\.[a-z]+}"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @Test
    public void recordQuantiles() throws Exception {
        mvc.perform(get("/api/c1/quantiles/10")).andExpect(status().isOk());
        assertThat(registry.findMeter(Gauge.class, "http_server_requests.quantiles", "quantile", "0.5")).isNotEmpty();
        assertThat(registry.findMeter(Gauge.class, "http_server_requests.quantiles", "quantile", "0.95")).isNotEmpty();
    }

    @SpringBootApplication(scanBasePackages = "isolated")
    @EnableMetrics
    @Import({ Controller1.class, Controller2.class })
    static class App {
        @Bean
        MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }
    }

    @RestController
    @RequestMapping("/api/c1")
    static class Controller1 {
        @Timed(extraTags = {"public", "true"})
        @GetMapping("/{id}")
        public String successfulWithExtraTags(@PathVariable Long id) {
            return id.toString();
        }

        @Timed // contains dimensions for status, etc. that can't be known until after the response is sent
        @Timed(value = "my_long_request", extraTags = {"region", "test"}, longTask = true) // in progress metric
        @GetMapping("/long/{id}")
        public Callable<String> takesLongTimeToSatisfy(@PathVariable Long id) {
            return () -> {
                try {
                    longRequestCountDown.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return id.toString();
            };
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

        @Timed(quantiles = {0.5, 0.95})
        @GetMapping("/quantiles/{id}")
        public String quantiles(@PathVariable String id) {
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