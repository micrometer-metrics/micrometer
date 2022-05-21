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
package io.micrometer.core.instrument.binder.http;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Tag;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Provides {@link Tag Tags} for HTTP Servlet request handling.
 *
 * @author Jon Schneider
 * @since 1.4.0
 */
@Incubating(since = "1.4.0")
@FunctionalInterface
public interface HttpServletRequestTagsProvider {

    /**
     * Provides tags to be associated with metrics for the given {@code request} and
     * {@code response} exchange.
     * @param request the request
     * @param response the response
     * @return tags to associate with metrics for the request and response exchange
     */
    Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response);

}
