package org.springframework.metrics.instrument.web;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.annotation.Timed;
import org.springframework.metrics.instrument.simple.SimpleTimer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MetricsHandlerExampleApp.class)
@WebMvcTest({MetricsHandlerInterceptorController1.class, MetricsHandlerInterceptorController2.class})
public class MetricsHandlerInterceptorTest {
    @Autowired
    MockMvc mvc;

    @MockBean
    MeterRegistry registry;

    @Test
    public void metricsGatheredWhenMethodIsTimed() throws Exception {
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
    public void metricsNotGatheredWhenRequestMappingIsNotTimed() throws Exception {
        mvc.perform(get("/api/c1/untimed/10")).andExpect(status().isOk());
        verify(registry, never()).timer(anyString(), any(Stream.class));
    }

    @Test
    public void metricsGatheredWhenControllerIsTimed() throws Exception {
        SimpleTimer timer = expectTimer();
        mvc.perform(get("/api/c2/10")).andExpect(status().isOk());
        assertTags(Tag.of("status", "200"));
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    public void metricsGatheredWhenClientRequestBad() throws Exception {
        SimpleTimer timer = expectTimer();
        mvc.perform(get("/api/c1/oops")).andExpect(status().is4xxClientError());
        assertTags(Tag.of("status", "400"), Tag.of("uri", "api_c1_-id-"));
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    public void metricsGatheredWhenUnhandledError() throws Exception {
        SimpleTimer timer = expectTimer();
        try {
            mvc.perform(get("/api/c1/unhandledError/10")).andExpect(status().isOk());
        } catch (Exception e) {
        }
        assertTags(Tag.of("exception", "RuntimeException"), Tag.of("status", "200"), Tag.of("uri", "api_c1_unhandledError_-id-"));
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    /* FIXME */ @Ignore("ErrorMvcAutoConfiguration is blowing up on SPEL evaluation of 'timestamp'")
    public void metricsGatheredWhenHandledError() throws Exception {
        SimpleTimer timer = expectTimer();
        mvc.perform(get("/api/c1/error/10")).andExpect(status().is4xxClientError());
        assertTags(Tag.of("status", "422"), Tag.of("uri", "api_c1_error_-id-"));
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    public void metricsGatheredWhenRegexEndpoint() throws Exception {
        SimpleTimer timer = expectTimer();
        mvc.perform(get("/api/c1/regex/.abc")).andExpect(status().isOk());
        assertTags(Tag.of("status", "200"), Tag.of("uri", "api_c1_regex_-id-"));
        assertThat(timer.count()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private void assertTags(Tag... match) {
        ArgumentCaptor<Stream> tags = ArgumentCaptor.forClass(Stream.class);
        verify(registry).timer(anyString(), tags.capture());
        assertThat((List) tags.getValue().collect(Collectors.toList())).contains(match);
    }

    private SimpleTimer expectTimer() {
        SimpleTimer timer = new SimpleTimer();

        //noinspection unchecked
        when(registry.timer(eq("rest"), any(Stream.class))).thenReturn(timer);
        return timer;
    }
}

@SpringBootApplication
class MetricsHandlerExampleApp {
}

@RestController
@RequestMapping("/api/c1")
class MetricsHandlerInterceptorController1 {
    @Timed(extraTags = {"public", "true"})
    @RequestMapping("/{id}")
    public String successfulWithExtraTags(@PathVariable Long id) {
        return id.toString();
    }

    @RequestMapping("/untimed/{id}")
    public String successfulButUntimed(@PathVariable Long id) {
        return id.toString();
    }

    @Timed
    @RequestMapping("/error/{id}")
    public String alwaysThrowsException(@PathVariable Long id) {
        throw new IllegalStateException("Boom on $id!");
    }

    @Timed
    @RequestMapping("/unhandledError/{id}")
    public String alwaysThrowsUnhandledException(@PathVariable Long id) {
        throw new RuntimeException("Boom on $id!");
    }

    @Timed
    @RequestMapping("/regex/{id:\\.[a-z]+}")
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
class MetricsHandlerInterceptorController2 {
    @RequestMapping("/{id}")
    public String successful(@PathVariable Long id) {
        return id.toString();
    }
}