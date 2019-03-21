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

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.Nullable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Provides {@link Tag Tags} for Spring MVC-based request handling.
 *
 * @author Jon Schneider
 * @author Andy Wilkinson
 */
@NonNullApi
public interface WebMvcTagsProvider {

    /**
     * Provides tags to be used by {@link LongTaskTimer long task timers}.
     *
     * @param request the HTTP request
     * @param handler the handler for the request
     * @return tags to associate with metrics recorded for the request
     */
    @NonNull
    Iterable<Tag> httpLongRequestTags(@Nullable HttpServletRequest request, @Nullable Object handler);

    /**
     * Provides tags to be associated with metrics for the given {@code request} and
     * {@code response} exchange.
     *
     * @param request  the request
     * @param response the response
     * @param handler  the handler for the request
     * @param ex       the current exception, if any
     * @return tags to associate with metrics for the request and response exchange
     */
    @NonNull
    Iterable<Tag> httpRequestTags(@Nullable HttpServletRequest request,
                                  @Nullable HttpServletResponse response,
                                  @Nullable Object handler,
                                  @Nullable Throwable ex);
}
