package org.springframework.metrics.instrument.web;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.metrics.instrument.Tag;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.stream.Stream;

/**
 * @author Jon Schneider
 */
public class EmptyWebMetricsTagProvider implements WebMetricsTagProvider {
    @Override
    public Stream<Tag> clientHttpRequestTags(HttpRequest request, ClientHttpResponse response) {
        return Stream.empty();
    }

    @Override
    public Stream<Tag> httpRequestTags(HttpServletRequest request, HttpServletResponse response, Object handler, String caller) {
        return Stream.empty();
    }
}
