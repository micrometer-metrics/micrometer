package org.springframework.metrics.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.metrics.export.prometheus.PrometheusFunctions;
import org.springframework.metrics.instrument.DistributionSummary;
import org.springframework.metrics.instrument.prometheus.PrometheusMeterRegistry;
import org.springframework.metrics.instrument.stats.hist.CumulativeHistogram;
import org.springframework.metrics.instrument.stats.quantile.CKMSQuantiles;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import javax.servlet.Servlet;
import java.net.UnknownHostException;

import static org.springframework.metrics.instrument.stats.hist.CumulativeHistogram.linear;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Demonstrates how a histogram can also contain quantiles.
 *
 * @author Jon Schneider
 */
public class PrometheusHistogramSample {
    public static void main(String[] args) throws UnknownHostException, LifecycleException, InterruptedException {
        RandomEngine r = new MersenneTwister64(0);
        Normal dist = new Normal(100, 50, r);

        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry();

        RouterFunction<ServerResponse> route =
                route(GET("/prometheus"), PrometheusFunctions.scrape(meterRegistry));

        startServer(route);

        DistributionSummary hist = meterRegistry.summaryBuilder("hist")
                .histogram(CumulativeHistogram.buckets(linear(0, 10, 20)))
                .quantiles(CKMSQuantiles
                        .quantile(0.95, 0.01)
                        .quantile(0.5, 0.05)
                        .create())
                .create();

        //noinspection InfiniteLoopStatement
        while(true) {
            Thread.sleep(10);
            long sample = (long) Math.max(0, dist.nextDouble());
            hist.record(sample);
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
