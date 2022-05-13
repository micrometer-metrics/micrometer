/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.registry.otlp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtlpConfigTest {

    @Test
    void resourceAttributesInputParsing() {
        OtlpConfig config = k -> "key1=value1,";
        assertThat(config.resourceAttributes()).containsEntry("key1", "value1").hasSize(1);
        config = k -> "k=v,a";
        assertThat(config.resourceAttributes()).containsEntry("k", "v").hasSize(1);
        config = k -> "k=v,a==";
        assertThat(config.resourceAttributes()).containsEntry("k", "v").containsEntry("a", "=").hasSize(2);
        config = k -> " k = v, a= b ";
        assertThat(config.resourceAttributes()).containsEntry("k", "v").containsEntry("a", "b").hasSize(2);
    }

}
