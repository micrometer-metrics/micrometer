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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.EnableMetrics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ControllerMetricsTest.App.class)
@WebMvcTest(ControllerMetricsTest.Controller1.class)
public class ControllerMetricsTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private SimpleMeterRegistry registry;

    @Test
    public void unhandledErrorCaughtByCustomExceptionHandler() throws Exception {
        assertThatCode(() -> mvc.perform(get("/api/c1/unhandledError/10")).andExpect(status().isOk()));
        assertThat(registry.findMeter(Timer.class, "http_server_requests", "exception", "RuntimeException"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @SpringBootApplication(scanBasePackages = "isolated")
    @EnableMetrics
    @Import(Controller1.class)
    static class App {
        @Bean
        MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }
    }

    @ControllerAdvice
    static class CustomExceptionHandler {
        @Autowired
        ControllerMetrics metrics;

        @ExceptionHandler
        ResponseEntity<String> handleDefaultError(HttpServletRequest request,
                                                            HttpServletResponse response,
                                                            Throwable ex) {
            metrics.record(request, response, ex);
            return new ResponseEntity<>("this is a custom exception body", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RestController
    @RequestMapping("/api/c1")
    static class Controller1 {
        @Bean
        public CustomExceptionHandler controllerAdvice() {
            return new CustomExceptionHandler();
        }

        @Timed
        @GetMapping("/unhandledError/{id}")
        public String alwaysThrowsUnhandledException(@PathVariable Long id) {
            throw new RuntimeException("Boom on $id!");
        }
    }
}
