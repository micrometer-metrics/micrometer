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

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * FIXME Add 3xx redirects to the "unmapped" class of requests for metrics, see https://github.com/spring-projects/spring-boot/commit/d0cf6b534bf55b718ea8bf0f2e311a974f523e1b#diff-21d31563f43a6c81400762a5b655fda0
 * FIXME Add 404 same as above
 * FIXME Deal with async requests, see https://github.com/spring-projects/spring-boot/commit/b8b4ea489e91280ebb88327d8c11d6b483a03566#diff-21d31563f43a6c81400762a5b655fda0
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
                    this.webMvcMetrics.record(request, response, null);
                } catch (NestedServletException e) {
                    this.webMvcMetrics.record(request, response, e.getCause());
                    throw e;
                }
            }
        }
    }
}
