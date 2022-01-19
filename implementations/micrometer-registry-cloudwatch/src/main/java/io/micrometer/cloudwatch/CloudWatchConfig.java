/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.cloudwatch;

import io.micrometer.api.instrument.config.InvalidConfigurationException;
import io.micrometer.api.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.api.instrument.step.StepRegistryConfig;

/**
 * Configuration for CloudWatch exporting.
 *
 * @author Dawid Kublik
 * @deprecated the micrometer-registry-cloudwatch implementation has been deprecated in favour of
 *             micrometer-registry-cloudwatch2, which uses AWS SDK for Java 2.x
 */
@Deprecated
public interface CloudWatchConfig extends StepRegistryConfig {

    int MAX_BATCH_SIZE = 20;

    @Override
    default String prefix() {
        return "cloudwatch";
    }

    default String namespace() {
        String v = get(prefix() + ".namespace");
        if (v == null)
            throw new MissingRequiredConfigurationException("namespace must be set to report metrics to CloudWatch");
        return v;
    }

    @Override
    default int batchSize() {
        String v = get(prefix() + ".batchSize");
        if (v == null) {
            return MAX_BATCH_SIZE;
        }
        int vInt = Integer.parseInt(v);
        if (vInt > MAX_BATCH_SIZE)
            throw new InvalidConfigurationException("batchSize must be <= " + MAX_BATCH_SIZE);

        return vInt;
    }

}
