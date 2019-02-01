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
package io.micrometer.spring.autoconfigure.export.kairos;

import io.micrometer.spring.autoconfigure.export.properties.StepRegistryPropertiesConfigAdapterTest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link KairosPropertiesConfigAdapter}.
 *
 * @author Stephane Nicoll
 */
public class KairosPropertiesConfigAdapterTest extends
        StepRegistryPropertiesConfigAdapterTest<KairosProperties, KairosPropertiesConfigAdapter> {

    @Override
    protected KairosProperties createProperties() {
        return new KairosProperties();
    }

    @Override
    protected KairosPropertiesConfigAdapter createConfigAdapter(
            KairosProperties properties) {
        return new KairosPropertiesConfigAdapter(properties);
    }

    @Test
    public void whenPropertiesUriIsSetAdapterUriReturnsIt() {
        KairosProperties properties = createProperties();
        properties.setUri("https://kairos.example.com:8080/api/v1/datapoints");
        assertThat(createConfigAdapter(properties).uri())
            .isEqualTo("https://kairos.example.com:8080/api/v1/datapoints");
    }

    @Test
    public void whenPropertiesUserNameIsSetAdapterUserNameReturnsIt() {
        KairosProperties properties = createProperties();
        properties.setUserName("alice");
        assertThat(createConfigAdapter(properties).userName()).isEqualTo("alice");
    }

    @Test
    public void whenPropertiesPasswordIsSetAdapterPasswordReturnsIt() {
        KairosProperties properties = createProperties();
        properties.setPassword("secret");
        assertThat(createConfigAdapter(properties).password()).isEqualTo("secret");
    }

}
