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
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.spring.autoconfigure.web.servlet.ServletMetricsConfiguration;
import io.prometheus.client.CollectorRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.util.NestedServletException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static io.micrometer.core.instrument.MockClock.clock;
import static io.micrometer.spring.web.servlet.MetricsFilterTest.RedirectAndNotFoundFilter.TEST_MISBEHAVE_HEADER;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Jon Schneider
 * @author Nikolay Rybak
 */
@RunWith(SpringRunner.class)
@WebAppConfiguration
@AutoConfigureMockMvc
@TestPropertySource(properties = "security.ignored=/**")
public class MetricsFilterTest {
    @Autowired
    private SimpleMeterRegistry registry;

    @Autowired
    private PrometheusMeterRegistry prometheusRegistry;

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("callableBarrier")
    private CyclicBarrier callableBarrier;

    @Autowired
    @Qualifier("completableFutureBarrier")
    private CyclicBarrier completableFutureBarrier;

    @Test
    public void timedMethod() throws Exception {
        mvc.perform(get("/api/c1/10")).andExpect(status().isOk());

        assertThat(registry.find("http.server.requests")
            .tags("status", "200", "uri", "/api/c1/{id}", "public", "true")
            .value(Statistic.Count, 1.0).timer()).isPresent();
    }

    @Test
    public void subclassedTimedMethod() throws Exception {
        mvc.perform(get("/api/c1/metaTimed/10")).andExpect(status().isOk());

        assertThat(registry.find("http.server.requests")
            .tags("status", "200", "uri", "/api/c1/metaTimed/{id}")
            .value(Statistic.Count, 1.0).timer()).isPresent();
    }

    @Test
    public void untimedMethod() throws Exception {
        mvc.perform(get("/api/c1/untimed/10")).andExpect(status().isOk());

        assertThat(registry.find("http.server.requests")
            .tags("uri", "/api/c1/untimed/10").timer()).isEmpty();
    }

    @Test
    public void timedControllerClass() throws Exception {
        mvc.perform(get("/api/c2/10")).andExpect(status().isOk());

        assertThat(registry.find("http.server.requests").tags("status", "200")
            .value(Statistic.Count, 1.0)
            .timer()).isPresent();
    }

    @Test
    public void badClientRequest() throws Exception {
        mvc.perform(get("/api/c1/oops")).andExpect(status().is4xxClientError());

        assertThat(registry.find("http.server.requests").tags("status", "400")
            .value(Statistic.Count, 1.0)
            .timer()).isPresent();
    }

    @Test
    public void redirectRequest() throws Exception {
        mvc.perform(get("/api/redirect")
            .header(TEST_MISBEHAVE_HEADER, "302")).andExpect(status().is3xxRedirection());

        assertThat(registry.find("http.server.requests")
            .tags("uri", "REDIRECTION")
            .tags("status", "302").timer()).isPresent();
    }

    @Test
    public void notFoundRequest() throws Exception {
        mvc.perform(get("/api/not/found")
            .header(TEST_MISBEHAVE_HEADER, "404"))
            .andExpect(status().is4xxClientError());

        assertThat(registry.find("http.server.requests")
            .tags("uri", "NOT_FOUND")
            .tags("status", "404").timer()).isPresent();
    }

    @Test
    public void unhandledError() throws Exception {
        assertThatCode(() -> mvc.perform(get("/api/c1/unhandledError/10"))
            .andExpect(status().isOk()))
            .hasRootCauseInstanceOf(RuntimeException.class);

        assertThat(registry.find("http.server.requests")
            .tags("exception", "RuntimeException").value(Statistic.Count, 1.0)
            .timer()).isPresent();
    }

    @Test
    public void asyncCallableRequest() throws Exception {
        final AtomicReference<MvcResult> result = new AtomicReference<>();
        Thread backgroundRequest = new Thread(() -> {
            try {
                result.set(mvc.perform(get("/api/c1/callable/10"))
                    .andExpect(request().asyncStarted())
                    .andReturn());
            } catch (Exception e) {
                fail("Failed to execute async request", e);
            }
        });

        backgroundRequest.start();

        // the request is not prematurely recorded as complete
        assertThat(registry.find("http.server.requests")
            .tags("uri", "/api/c1/callable/{id}").timer()).isNotPresent();

        // once the mapping completes, we can gather information about status, etc.
        callableBarrier.await();
        clock(registry).add(Duration.ofSeconds(2));

        // while the mapping is running, it contributes to the activeTasks count
        assertThat(registry.find("my.long.request")
            .tags("region", "test")
            .longTaskTimer())
            .hasValueSatisfying(ltt -> assertThat(ltt.activeTasks()).isEqualTo(1));

        callableBarrier.await();

        backgroundRequest.join();

        mvc.perform(asyncDispatch(result.get())).andExpect(status().isOk());

        assertThat(registry.find("http.server.requests")
            .tags("status", "200")
            .tags("uri", "/api/c1/callable/{id}")
            .value(Statistic.Count, 1.0)
            .timer())
            .isPresent()
            .hasValueSatisfying(t -> assertThat(t.totalTime(TimeUnit.SECONDS)).isEqualTo(2));

        // once the async dispatch is complete, it should no longer contribute to the activeTasks count
        assertThat(registry.find("my.long.request")
            .tags("region", "test")
            .longTaskTimer())
            .hasValueSatisfying(ltt -> assertThat(ltt.activeTasks()).isEqualTo(0));
    }

    @Test
    public void asyncRequestThatThrowsUncheckedException() throws Exception {
        final MvcResult result = mvc.perform(get("/api/c1/completableFutureException"))
            .andExpect(request().asyncStarted())
            .andReturn();

        // once the async dispatch is complete, it should no longer contribute to the activeTasks count
        assertThat(registry.find("my.long.request.exception")
            .longTaskTimer())
            .hasValueSatisfying(ltt -> assertThat(ltt.activeTasks()).isEqualTo(1));

        assertThatExceptionOfType(NestedServletException.class)
            .isThrownBy(() -> mvc.perform(asyncDispatch(result)))
            .withRootCauseInstanceOf(RuntimeException.class);

        assertThat(registry.find("http.server.requests")
            .tags("uri", "/api/c1/completableFutureException")
            .value(Statistic.Count, 1.0)
            .timer())
            .isPresent();

        // once the async dispatch is complete, it should no longer contribute to the activeTasks count
        assertThat(registry.find("my.long.request.exception")
            .longTaskTimer())
            .hasValueSatisfying(ltt -> assertThat(ltt.activeTasks()).isEqualTo(0));
    }

    @Test
    public void asyncCompletableFutureRequest() throws Exception {
        final AtomicReference<MvcResult> result = new AtomicReference<>();
        Thread backgroundRequest = new Thread(() -> {
            try {
                result.set(mvc.perform(get("/api/c1/completableFuture/{id}", 1))
                    .andExpect(request().asyncStarted())
                    .andReturn());
            } catch (Exception e) {
                fail("Failed to execute async request", e);
            }
        });

        backgroundRequest.start();

        completableFutureBarrier.await();
        clock(registry).add(Duration.ofSeconds(2));
        completableFutureBarrier.await();

        backgroundRequest.join();

        mvc.perform(asyncDispatch(result.get())).andExpect(status().isOk());

        assertThat(registry.find("http.server.requests")
            .tags("uri", "/api/c1/completableFuture/{id}")
            .value(Statistic.Count, 1.0)
            .timer())
            .isPresent()
            .hasValueSatisfying(t -> assertThat(t.totalTime(TimeUnit.SECONDS)).isEqualTo(2));
    }

    @Test
    public void endpointThrowsError() throws Exception {
        mvc.perform(get("/api/c1/error/10")).andExpect(status().is4xxClientError());

        assertThat(registry.find("http.server.requests").tags("status", "422")
            .value(Statistic.Count, 1.0).timer()).isPresent();
    }

    @Test
    public void regexBasedRequestMapping() throws Exception {
        mvc.perform(get("/api/c1/regex/.abc")).andExpect(status().isOk());

        assertThat(registry.find("http.server.requests")
            .tags("uri", "/api/c1/regex/{id:\\.[a-z]+}").value(Statistic.Count, 1.0)
            .timer()).isPresent();
    }

    @Test
    public void recordQuantiles() throws Exception {
        mvc.perform(get("/api/c1/percentiles/10")).andExpect(status().isOk());

        assertThat(prometheusRegistry.scrape()).contains("quantile=\"0.5\"");
        assertThat(prometheusRegistry.scrape()).contains("quantile=\"0.95\"");
    }

    @Test
    public void recordHistogram() throws Exception {
        mvc.perform(get("/api/c1/histogram/10")).andExpect(status().isOk());

        assertThat(prometheusRegistry.scrape()).contains("le=\"0.001\"");
        assertThat(prometheusRegistry.scrape()).contains("le=\"30.0\"");
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Timed(percentiles = 0.95)
    public @interface Timed95 {
    }

    @Configuration
    @EnableWebMvc
    @Import({ServletMetricsConfiguration.class, Controller1.class, Controller2.class})
    static class MetricsFilterApp {
        @Bean
        Clock micrometerClock() {
            return new MockClock();
        }

        @Primary
        @Bean
        MeterRegistry meterRegistry(Collection<MeterRegistry> registries, Clock clock) {
            CompositeMeterRegistry composite = new CompositeMeterRegistry(clock);
            registries.forEach(composite::add);
            return composite;
        }

        @Bean
        SimpleMeterRegistry simple(Clock clock) {
            return new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        }

        @Bean
        PrometheusMeterRegistry prometheus(Clock clock) {
            PrometheusMeterRegistry r = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new CollectorRegistry(), clock);
            r.config().meterFilter(new MeterFilter() {
                @Override
                public MeterFilterReply accept(Meter.Id id) {
                    for (Tag tag : id.getTags()) {
                        if (tag.getKey().equals("uri") && (tag.getValue().contains("histogram") || tag.getValue().contains("percentiles"))) {
                            return MeterFilterReply.ACCEPT;
                        }
                    }
                    return MeterFilterReply.DENY;
                }
            });
            return r;
        }

        @Bean
        RedirectAndNotFoundFilter redirectAndNotFoundFilter() {
            return new RedirectAndNotFoundFilter();
        }

        @Bean(name = "callableBarrier")
        CyclicBarrier callableBarrier() {
            return new CyclicBarrier(2);
        }

        @Bean(name = "completableFutureBarrier")
        CyclicBarrier completableFutureBarrier() {
            return new CyclicBarrier(2);
        }
    }

    @RestController
    @RequestMapping("/api/c1")
    static class Controller1 {
        @Autowired
        @Qualifier("callableBarrier")
        private CyclicBarrier callableBarrier;

        @Autowired
        @Qualifier("completableFutureBarrier")
        private CyclicBarrier completableFutureBarrier;

        @Timed(extraTags = {"public", "true"})
        @GetMapping("/{id}")
        public String successfulWithExtraTags(@PathVariable Long id) {
            return id.toString();
        }

        @Timed
        @Timed(value = "my.long.request", extraTags = {"region", "test"}, longTask = true)
        @GetMapping("/callable/{id}")
        public Callable<String> asyncCallable(@PathVariable Long id) throws Exception {
            callableBarrier.await();
            return () -> {
                try {
                    callableBarrier.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return id.toString();
            };
        }

        @Timed
        @GetMapping("/completableFuture/{id}")
        CompletableFuture<String> asyncCompletableFuture(@PathVariable Long id) throws Exception {
            completableFutureBarrier.await();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    completableFutureBarrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
                return id.toString();
            });
        }

        @Timed
        @Timed(value = "my.long.request.exception", longTask = true)
        @GetMapping("/completableFutureException")
        CompletableFuture<String> asyncCompletableFutureException() throws Exception {
            return CompletableFuture.supplyAsync(() -> {
                throw new RuntimeException("boom");
            });
        }

        @GetMapping("/untimed/{id}")
        public String successfulButUntimed(@PathVariable Long id) {
            return id.toString();
        }

        @Timed
        @GetMapping("/error/{id}")
        public String alwaysThrowsException(@PathVariable Long id) {
            throw new IllegalStateException("Boom on " + id + "!");
        }

        @Timed
        @GetMapping("/unhandledError/{id}")
        public String alwaysThrowsUnhandledException(@PathVariable Long id) {
            throw new RuntimeException("Boom on " + id + "!");
        }

        @Timed
        @GetMapping("/regex/{id:\\.[a-z]+}")
        public String successfulRegex(@PathVariable String id) {
            return id;
        }

        @Timed(percentiles = {0.50, 0.95})
        @GetMapping("/percentiles/{id}")
        public String percentiles(@PathVariable String id) {
            return id;
        }

        @Timed(histogram = true)
        @GetMapping("/histogram/{id}")
        public String histogram(@PathVariable String id) {
            return id;
        }

        @Timed95
        @GetMapping("/metaTimed/{id}")
        public String meta(@PathVariable String id) {
            return id;
        }

        @ExceptionHandler(IllegalStateException.class)
        @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
        ModelAndView defaultErrorHandler(HttpServletRequest request, Exception e) {
            return new ModelAndView("myerror");
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

    static class RedirectAndNotFoundFilter extends OncePerRequestFilter {

        static final String TEST_MISBEHAVE_HEADER = "x-test-misbehave-status";

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
            String misbehave = request.getHeader(TEST_MISBEHAVE_HEADER);
            if (misbehave != null) {
                response.setStatus(Integer.parseInt(misbehave));
            } else {
                filterChain.doFilter(request, response);
            }
        }
    }
}
