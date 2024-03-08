/*
 * Copyright 2020 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.javalin.samples;

import io.javalin.Javalin;
import io.javalin.http.ExceptionHandler;
import io.javalin.http.HandlerType;
import io.javalin.plugin.Plugin;
import io.javalin.routing.HandlerEntry;
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.http.DefaultHttpJakartaServletRequestTagsProvider;
import io.micrometer.core.instrument.binder.jetty.JettyConnectionMetrics;
import io.micrometer.core.instrument.binder.jetty.JettyServerThreadPoolMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.jetty11.TimedHandler;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

/**
 * See https://github.com/tipsy/javalin/pull/959 which adds improvements to
 * MicrometerPlugin
 */
public class PrometheusSample {

    public static void main(String[] args) {
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        // add any tags here that will apply to all metrics streaming from this app
        // (e.g. EC2 region, stack, instance id, server group)
        meterRegistry.config().commonTags("app", "javalin-sample");

        new JvmGcMetrics().bindTo(meterRegistry);
        new JvmHeapPressureMetrics().bindTo(meterRegistry);
        new JvmMemoryMetrics().bindTo(meterRegistry);
        new ProcessorMetrics().bindTo(meterRegistry);
        new FileDescriptorMetrics().bindTo(meterRegistry);

        Javalin app = Javalin.create(config -> config.plugins.register(new MicrometerPlugin(meterRegistry)))
            .start(8080);

        // must manually delegate to Micrometer exception handler for excepton tags to be
        // correct
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            MicrometerPlugin.EXCEPTION_HANDLER.handle(e, ctx);
            e.printStackTrace();
        });

        app.get("/", ctx -> ctx.result("Hello World"));
        app.get("/hello/{name}", ctx -> ctx.result("Hello: " + ctx.pathParam("name")));
        app.get("/boom", ctx -> {
            throw new IllegalArgumentException("boom");
        });

        app.routes(() -> {
            path("hi", () -> {
                get("{name}", ctx -> ctx.result("Hello: " + ctx.pathParam("name")));
            });
        });

        app.after("/hello/*", ctx -> {
            System.out.println("hello");
        });

        app.get("/prometheus",
                ctx -> ctx.contentType("text/plain; version=0.0.4; charset=utf-8").result(meterRegistry.scrape()));
    }

}

class MicrometerPlugin implements Plugin {

    private static final String EXCEPTION_HEADER = "__micrometer_exception_name";

    private final MeterRegistry registry;

    private final Iterable<Tag> tags;

    public static ExceptionHandler<Exception> EXCEPTION_HANDLER = (e, ctx) -> {
        String simpleName = e.getClass().getSimpleName();
        ctx.header(EXCEPTION_HEADER, StringUtils.isNotBlank(simpleName) ? simpleName : e.getClass().getName());
        ctx.status(500);
    };

    public MicrometerPlugin(MeterRegistry registry) {
        this(registry, Tags.empty());
    }

    public MicrometerPlugin(MeterRegistry registry, Iterable<Tag> tags) {
        this.registry = registry;
        this.tags = tags;
    }

    @Override
    public void apply(@NonNull Javalin app) {
        Server server = app.jettyServer().server();

        app.exception(Exception.class, EXCEPTION_HANDLER);

        server.insertHandler(new TimedHandler(registry, tags, new DefaultHttpJakartaServletRequestTagsProvider() {
            @Override
            public Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response) {
                String exceptionName = response.getHeader(EXCEPTION_HEADER);
                response.setHeader(EXCEPTION_HEADER, null);

                String uri = app.javalinServlet()
                    .getMatcher()
                    .findEntries(HandlerType.valueOf(request.getMethod()), request.getPathInfo())
                    .stream()
                    .findAny()
                    .map(HandlerEntry::getPath)
                    .map(path -> path.equals("/") || StringUtils.isBlank(path) ? "root" : path)
                    .map(path -> response.getStatus() >= 300 && response.getStatus() < 400 ? "REDIRECTION" : path)
                    .map(path -> response.getStatus() == 404 ? "NOT_FOUND" : path)
                    .orElse("unknown");

                return Tags.concat(super.getTags(request, response), "uri", uri, "exception",
                        exceptionName == null ? "None" : exceptionName);
            }
        }));

        new JettyServerThreadPoolMetrics(server.getThreadPool(), tags).bindTo(registry);
        new JettyConnectionMetrics(registry, tags);
    }

}
