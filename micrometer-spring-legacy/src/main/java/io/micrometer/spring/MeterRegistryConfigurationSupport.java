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
package io.micrometer.spring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.Collection;

/**
 * Post-construction setup of a meter registry, regardless of its backing implementation.
 *
 * @author Jon Schneider
 */
@Configuration
public class MeterRegistryConfigurationSupport {
    private final MeterRegistry registry;
    private final Collection<MeterBinder> binders;
    private final Collection<MeterRegistryConfigurer> registryConfigurers;

    public MeterRegistryConfigurationSupport(MeterRegistry registry,
                                             ObjectProvider<Collection<MeterBinder>> binders,
                                             ObjectProvider<Collection<MeterRegistryConfigurer>> registryConfigurers) {
        this.registry = registry;
        this.binders = binders.getIfAvailable();
        this.registryConfigurers = registryConfigurers.getIfAvailable();
    }

    @PostConstruct
    void bindAll() {
        // Important that this happens before binders are applied, as it
        // may involve adding common tags that should apply to metrics registered
        // in those binders.
        if (registryConfigurers != null) {
            registryConfigurers.forEach(conf -> conf.configureRegistry(registry));
        }

        if (binders != null) {
            binders.forEach(binder -> binder.bindTo(registry));
        }
    }
}
