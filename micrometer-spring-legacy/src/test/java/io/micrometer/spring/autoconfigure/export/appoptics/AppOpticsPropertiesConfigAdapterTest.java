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
package io.micrometer.spring.autoconfigure.export.appoptics;

import io.micrometer.spring.autoconfigure.export.properties.StepRegistryPropertiesConfigAdapterTest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AppOpticsPropertiesConfigAdapter}.
 *
 * @author Stephane Nicoll
 */
public class AppOpticsPropertiesConfigAdapterTest extends
        StepRegistryPropertiesConfigAdapterTest<AppOpticsProperties, AppOpticsPropertiesConfigAdapter> {

    @Override
    protected AppOpticsProperties createProperties() {
        return new AppOpticsProperties();
    }

    @Override
    protected AppOpticsPropertiesConfigAdapter createConfigAdapter(
            AppOpticsProperties properties) {
        return new AppOpticsPropertiesConfigAdapter(properties);
    }

    @Test
    public void whenPropertiesUrisIsSetAdapterUriReturnsIt() {
        AppOpticsProperties properties = createProperties();
        properties.setUri("https://appoptics.example.com/v1/measurements");
        assertThat(createConfigAdapter(properties).uri())
                .isEqualTo("https://appoptics.example.com/v1/measurements");
    }

    @Test
    public void whenPropertiesApiTokenIsSetAdapterApiTokenReturnsIt() {
        AppOpticsProperties properties = createProperties();
        properties.setApiToken("ABC123");
        assertThat(createConfigAdapter(properties).apiToken()).isEqualTo("ABC123");
    }

    @Test
    public void whenPropertiesHostTagIsSetAdapterHostTagReturnsIt() {
        AppOpticsProperties properties = createProperties();
        properties.setHostTag("node");
        assertThat(createConfigAdapter(properties).hostTag()).isEqualTo("node");
    }

}
