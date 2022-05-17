package io.micrometer.boot2.samples;

import java.util.List;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import io.micrometer.boot2.samples.components.PersonController;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.TimerObservationHandler;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.http.context.HttpClientContext;
import io.micrometer.observation.transport.http.context.HttpServerContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.*;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.HttpClientTracingObservationHandler;
import io.micrometer.tracing.handler.HttpServerTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.http.HttpClientHandler;
import io.micrometer.tracing.http.HttpServerHandler;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.brave.ZipkinSpanHandler;
import zipkin2.reporter.urlconnection.URLConnectionSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackageClasses = PersonController.class)
@EnableScheduling
public class ObservationSample {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ObservationSample.class).profiles("observation").run(args);
    }

    @Bean
    ObservationRegistry observationRegistry(List<ObservationHandler<? extends Observation.Context>> observationHandlers) {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        ObservationRegistry.ObservationConfig observationConfig = observationRegistry.observationConfig();
        observationHandlers.forEach(observationConfig::observationHandler);

        return observationRegistry;
    }

    @Bean
    ObservationHandler<Observation.Context> timerObservationHandler(MeterRegistry meterRegistry) {
        return new TimerObservationHandler(meterRegistry);
    }

    @Bean
    ObservationHandler<Observation.Context> traceObservationHandler(Tracer tracer, HttpServerHandler httpServerHandler, HttpClientHandler httpClientHandler) {
        TracingObservationHandler<HttpServerContext> httpServerTracingObservationHandler = new HttpServerTracingObservationHandler(tracer, httpServerHandler);
        TracingObservationHandler<HttpClientContext> httpClientTracingObservationHandler = new HttpClientTracingObservationHandler(tracer, httpClientHandler);
        TracingObservationHandler<Observation.Context> defaultTracingObservationHandler = new DefaultTracingObservationHandler(tracer);

        return new ObservationHandler.FirstMatchingCompositeObservationHandler(httpServerTracingObservationHandler, httpClientTracingObservationHandler, defaultTracingObservationHandler);
    }

    @Bean
    Tracer tracer(Tracing tracing) {
        return new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()), new BraveBaggageManager());
    }

    @Bean
    Tracing tracing(AsyncReporter<Span> reporter, @Value("${spring.application.name}") String applicationName) {
        return Tracing.newBuilder()
                .localServiceName(applicationName)
                .addSpanHandler(ZipkinSpanHandler.newBuilder(reporter).build())
                .sampler(Sampler.ALWAYS_SAMPLE)
                .build();
    }

    @Bean
    AsyncReporter<Span> reporter(Sender sender) {
        return AsyncReporter
                .builder(sender)
                .build();
    }

    @Bean
    Sender sender(@Value("${observation.zipkin.url}") String zipkinUrl) {
        return URLConnectionSender.newBuilder()
                .connectTimeout(1000)
                .readTimeout(1000)
                .endpoint((zipkinUrl.endsWith("/") ? zipkinUrl.substring(0, zipkinUrl.length() - 1) : zipkinUrl) + "/api/v2/spans").build();
    }

    @Bean
    HttpTracing httpTracing(Tracing tracing) {
        return HttpTracing.newBuilder(tracing).build();
    }

    @Bean
    HttpServerHandler httpServerHandler(HttpTracing httpTracing) {
        return new BraveHttpServerHandler(brave.http.HttpServerHandler.create(httpTracing));
    }

    @Bean
    HttpClientHandler httpClientHandler(HttpTracing httpTracing) {
        return new BraveHttpClientHandler(brave.http.HttpClientHandler.create(httpTracing));
    }
}
