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
package io.micrometer.wavefront;

import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;

public interface WavefrontConfig extends StepRegistryConfig {
    WavefrontConfig DEFAULT = k -> null;

    String _proxyHost_ = ".proxyHost";
    String _proxyPort_ = ".proxyPort";
    String _namePrefix_ = ".namePrefix";

    @Override
    default Duration step() {
        String v = get(prefix() + ".step");
        return v == null ? Duration.ofSeconds(10) : Duration.parse(v);
    }

    @Override
    default String prefix() {
        return "wavefront";
    }
    default boolean test() { return false; }

    @Nullable
    default String getHost() {
        String v = get(prefix() + _proxyHost_);
        return v == null ? "localhost" : v;
    }
    @Nullable
    default String getPort() {
        String v = get(prefix() + _proxyPort_);
        return v == null ? "2878" : v;
    }
    @Nullable
    default String getNamePrefix() {
        String v = get(prefix() + _namePrefix_);
        return v;
    }
}
