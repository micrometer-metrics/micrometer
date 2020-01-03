package io.micrometer.core.instrument.binder.http;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Tag;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *  Provides {@link Tag Tags} for HTTP request handling.
 *
 *  @author Jon Schneider
 *  @since 1.4.0
 */
@Incubating(since = "1.4.0")
public interface HttpRequestTagsProvider {
    /**
     * Provides tags to be associated with metrics for the given {@code request} and
     * {@code response} exchange.
     * @param request the request
     * @param response the response
     * @return tags to associate with metrics for the request and response exchange
     */
    Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response);
}
