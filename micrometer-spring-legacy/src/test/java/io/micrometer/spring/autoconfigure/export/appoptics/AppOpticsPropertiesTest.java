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
package io.micrometer.spring.autoconfigure.export.appoptics;

import io.micrometer.appoptics.AppOpticsConfig;
import io.micrometer.spring.autoconfigure.export.properties.StepRegistryPropertiesTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AppOpticsProperties}.
 *
 * @author Stephane Nicoll
 */
public class AppOpticsPropertiesTest extends StepRegistryPropertiesTest {

    @Override
    public void defaultValuesAreConsistent() {
        AppOpticsProperties properties = new AppOpticsProperties();
        AppOpticsConfig config = (key) -> null;
        assertStepRegistryDefaultValues(properties, config);
        assertThat(properties.getUri()).isEqualToIgnoringWhitespace(config.uri());
        assertThat(properties.getHostTag()).isEqualToIgnoringWhitespace(config.hostTag());
    }

}
