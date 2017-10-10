/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.web.servlet;

import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Intercepts incoming HTTP requests and records metrics about execution time and results.
 *
 * @author Jon Schneider
 */
public class MetricsHandlerInterceptor extends HandlerInterceptorAdapter {

    private final WebMvcMetrics webMvcMetrics;

    public MetricsHandlerInterceptor(WebMvcMetrics webMvcMetrics) {
        this.webMvcMetrics = webMvcMetrics;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        this.webMvcMetrics.preHandle(request, handler);
        return super.preHandle(request, response, handler);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        this.webMvcMetrics.record(request, response, ex);
        super.afterCompletion(request, response, handler, ex);
    }

}
