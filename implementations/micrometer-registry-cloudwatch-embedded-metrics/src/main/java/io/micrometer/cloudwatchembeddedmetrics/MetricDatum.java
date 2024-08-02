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
package io.micrometer.cloudwatchembeddedmetrics;

import io.micrometer.common.lang.Nullable;
import software.amazon.cloudwatchlogs.emf.model.DimensionSet;
import software.amazon.cloudwatchlogs.emf.model.StorageResolution;
import software.amazon.cloudwatchlogs.emf.model.Unit;

/**
 * @author Kyle Sletmoe
 * @author Klaus Hartl
 * @since 1.14.0
 */
public class MetricDatum {

    final String metricName;

    final @Nullable DimensionSet dimensions;

    final double value;

    final Unit unit;

    final StorageResolution storageResolution;

    public MetricDatum(String metricName, @Nullable DimensionSet dimensions, double value, Unit unit,
            StorageResolution storageResolution) {
        this.metricName = metricName;
        this.dimensions = dimensions;
        this.value = value;
        this.unit = unit;
        this.storageResolution = storageResolution;
    }

}
