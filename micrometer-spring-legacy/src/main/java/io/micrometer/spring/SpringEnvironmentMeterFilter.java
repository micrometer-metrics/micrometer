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
package io.micrometer.spring;

import io.micrometer.core.instrument.config.PropertyMeterFilter;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.Environment;

/**
 * @author Jon Schneider
 */
public class SpringEnvironmentMeterFilter extends PropertyMeterFilter {
    private final Environment environment;
    private final DefaultConversionService conversionService = new DefaultConversionService();

    public SpringEnvironmentMeterFilter(Environment environment) {
        this.environment = environment;
        this.conversionService.addConverter(new StringToDurationConverter());
    }

    @Override
    public <V> V get(String k, Class<V> vClass) {
        if(conversionService.canConvert(String.class, vClass)) {
            return conversionService.convert(environment.getProperty("spring.metrics.filter." + k), vClass);
        }
        return null;
    }
}
