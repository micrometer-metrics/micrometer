/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.export.prometheus;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enable an endpoint that exposes Prometheus metrics from its default collector.
 * <p>
 * Usage:
 * <br>Just add this annotation to the main class of your Spring Boot application, e.g.:
 * <pre><code>
 * {@literal @}SpringBootApplication
 * {@literal @}EnablePrometheusScraping
 *  public class Application {
 *
 *    public static void main(String[] args) {
 *      SpringApplication.run(Application.class, args);
 *    }
 *  }
 * </code></pre>
 * <p>
 * Configuration:
 * <br>You can customize this endpoint at runtime using the following spring properties:
 * <ul>
 * <li>{@code endpoints.prometheus.id} (default: "prometheus")</li>
 * <li>{@code endpoints.prometheus.enabled} (default: {@code true})</li>
 * <li>{@code endpoints.prometheus.sensitive} (default: {@code true})</li>
 * </ul>
 *
 * @author Marco Aust
 * @author Eliezio Oliveira
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(PrometheusEndpointConfiguration.class)
public @interface EnablePrometheusScraping {
}