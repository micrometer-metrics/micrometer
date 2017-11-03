package io.micrometer.spring.web.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.servlet.handler.MatchableHandlerMapping;
import org.springframework.web.util.NestedServletException;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Intercepts incoming HTTP requests and records metrics about execution time and results.
 *
 * @author Jon Schneider
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MetricsFilter extends OncePerRequestFilter {
    private final WebMvcMetrics webMvcMetrics;
    private final HandlerMappingIntrospector mappingIntrospector;
    private final Logger logger = LoggerFactory.getLogger(MetricsFilter.class);

    public MetricsFilter(WebMvcMetrics webMvcMetrics, HandlerMappingIntrospector mappingIntrospector) {
        this.webMvcMetrics = webMvcMetrics;
        this.mappingIntrospector = mappingIntrospector;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        HandlerExecutionChain handler;
        try {
            MatchableHandlerMapping matchableHandlerMapping = mappingIntrospector.getMatchableHandlerMapping(request);
            handler = matchableHandlerMapping.getHandler(request);
        } catch (Exception e) {
            logger.debug("Unable to time request", e);
            return;
        }

        if (handler != null) {
            Object handlerObject = handler.getHandler();
            if (handlerObject != null) {
                this.webMvcMetrics.preHandle(request, handlerObject);
                try {
                    filterChain.doFilter(request, response);

                    if(!request.isAsyncStarted()) {
                        this.webMvcMetrics.record(request, response, null);
                    }
                    else {
                        request.getAsyncContext().addListener(new AsyncListener() {
                            @Override
                            public void onComplete(AsyncEvent event) throws IOException {
                                record(event);
                            }

                            @Override
                            public void onTimeout(AsyncEvent event) throws IOException {
                                record(event);
                            }

                            @Override
                            public void onError(AsyncEvent event) throws IOException {
                                record(event);
                            }

                            @Override
                            public void onStartAsync(AsyncEvent event) throws IOException {
                            }

                            private void record(AsyncEvent event) {
                                if(event.getSuppliedResponse() instanceof HttpServletResponse &&
                                    event.getSuppliedRequest() instanceof HttpServletRequest) {
                                    MetricsFilter.this.webMvcMetrics.record(
                                        (HttpServletRequest) event.getSuppliedRequest(),
                                        (HttpServletResponse) event.getSuppliedResponse(),
                                        event.getThrowable()
                                    );
                                }
                            }
                        });
                    }

                } catch (NestedServletException e) {
                    this.webMvcMetrics.record(request, response, e.getCause());
                    throw e;
                }
            }
        }
    }
}
