/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.web.servlet;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.Nullable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

/**
 * Default implementation of {@link WebMvcTagsProvider}.
 *
 * @author Jon Schneider
 */
@NonNullApi
public class DefaultWebMvcTagsProvider implements WebMvcTagsProvider {

    /**
     * Supplies default tags to long task timers.
     *
     * @param request The HTTP request.
     * @param handler The request method that is responsible for handling the request.
     * @return A set of tags added to every Spring MVC HTTP request
     */
    @Override
    public Iterable<Tag> httpLongRequestTags(@Nullable HttpServletRequest request, @Nullable Object handler) {
        return Arrays.asList(WebMvcTags.method(request), WebMvcTags.uri(request, null));
    }

    /**
     * Supplies default tags to the Web MVC server programming model.
     *
     * @param request  The HTTP request.
     * @param response The HTTP response.
     * @param ex       The current exception, if any
     * @return A set of tags added to every Spring MVC HTTP request.
     */
    @Override
    public Iterable<Tag> httpRequestTags(@Nullable HttpServletRequest request,
                                         @Nullable HttpServletResponse response,
                                         @Nullable Object handler,
                                         @Nullable Throwable ex) {
        return Arrays.asList(WebMvcTags.method(request), WebMvcTags.uri(request, response),
            WebMvcTags.exception(ex), WebMvcTags.status(response), WebMvcTags.outcome(response));
    }

}
