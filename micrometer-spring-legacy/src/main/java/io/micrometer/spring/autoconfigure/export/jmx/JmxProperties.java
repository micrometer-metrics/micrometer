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
package io.micrometer.spring.autoconfigure.export.jmx;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@link ConfigurationProperties} for configuring JMX metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "spring.metrics.export.jmx")
public class JmxProperties {
    /**
     * The step size (reporting frequency) to use.
     */
    private Duration step = Duration.ofMinutes(1);

    public void setStep(Duration step) {
        this.step = step;
    }

    public Duration getStep() {
        return step;
    }
}
