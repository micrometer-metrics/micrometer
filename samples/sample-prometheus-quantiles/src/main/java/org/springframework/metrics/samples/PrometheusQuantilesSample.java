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
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.prometheus.PrometheusMeterRegistry;
import org.springframework.metrics.instrument.stats.CKMSQuantiles;
import org.springframework.metrics.instrument.stats.Frugal2UQuantiles;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import javax.servlet.Servlet;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

public class PrometheusQuantilesSample {
    public static void main(String[] args) throws UnknownHostException, LifecycleException {
        RandomEngine r = new MersenneTwister64(0);
        Normal dist = new Normal(100, 50, r);

        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry();

        RouterFunction<ServerResponse> route =
                route(GET("/prometheus"), PrometheusFunctions.scrape(meterRegistry));

        startServer(route);

        Timer ckmsTimer = meterRegistry.timerBuilder("random_ckms")
                .quantiles(CKMSQuantiles.build()
                        .quantile(0.5, 0.05)
                        .quantile(0.95, 0.01)
                        .create())
                .create();

        Timer frugalTimer = meterRegistry.timerBuilder("random_frugal")
                .quantiles(Frugal2UQuantiles.build()
                        .quantile(0.5, 10)
                        .quantile(0.95, 10)
                        .create())
                .create();

        while(true) {
            long sample = (long) Math.max(0, dist.nextDouble());
            ckmsTimer.record(sample, TimeUnit.SECONDS);
            frugalTimer.record(sample, TimeUnit.SECONDS);
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
