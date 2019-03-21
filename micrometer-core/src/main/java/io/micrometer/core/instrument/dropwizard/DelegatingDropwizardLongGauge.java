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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Gauge;

import java.util.function.Supplier;

/**
 * A Dropwizard gauge that can be created and registered with the Dropwizard registry and which
 * delegates to a double supplier that can be changed after registration.
 */
class DelegatingDropwizardLongGauge implements Gauge<Long> {
    private Supplier<Long> delegate = () -> 0L;

    public void setDelegate(Supplier<Long> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Long getValue() {
        return delegate.get();
    }
}
