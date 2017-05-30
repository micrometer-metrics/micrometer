package org.springframework.metrics.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Histogram;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.metrics.export.prometheus.PrometheusFunctions;
import org.springframework.metrics.instrument.prometheus.PrometheusMeterRegistry;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import javax.servlet.Servlet;
import java.net.UnknownHostException;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

public class PrometheusHistogramSample {
    public static void main(String[] args) throws UnknownHostException, LifecycleException, InterruptedException {
        RandomEngine r = new MersenneTwister64(0);
        Normal dist = new Normal(100, 50, r);

        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry();

        RouterFunction<ServerResponse> route =
                route(GET("/prometheus"), PrometheusFunctions.scrape(meterRegistry));

        startServer(route);

        CollectorRegistry prometheusRegistry = meterRegistry.getPrometheusRegistry();

        Histogram hist = Histogram.build("hist", " ")
                .linearBuckets(0, 10, 20) // or
                .create()
                .register(prometheusRegistry);

        //noinspection InfiniteLoopStatement
        while(true) {
            Thread.sleep(10);
            long sample = (long) Math.max(0, dist.nextDouble());
            hist.observe(sample);
        }
    }

    private static void startServer(RouterFunction<ServerResponse> route) throws LifecycleException {
        HttpHandler httpHandler = RouterFunctions.toHttpHandler(route);
        Servlet servlet = new ServletHttpHandlerAdapter(httpHandler);
        Tomcat server = new Tomcat();
        Context rootContext = server.addContext("", System.getProperty("java.io.tmpdir"));
        Tomcat.addServlet(rootContext, "servlet", servlet);
        rootContext.addServletMappingDecoded("/", "servlet");
        server.start();
    }
}
