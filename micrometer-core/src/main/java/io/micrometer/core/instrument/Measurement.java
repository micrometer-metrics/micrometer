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
package io.micrometer.core.instrument;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * A measurement sampled from a meter.
 *
 * @author Clint Checketts
 * @author Jon Schneider
 * @author Philippe Marschall
 */
public class Measurement {

    private final DoubleSupplier f;

    private final Statistic statistic;

    /**
     * Create a {@code Measurement} instance.
     * @param valueFunction value function
     * @param statistic statistic type
     * @since 1.10.0
     */
    public Measurement(DoubleSupplier valueFunction, Statistic statistic) {
        this.f = valueFunction;
        this.statistic = statistic;
    }

    public Measurement(Supplier<Double> valueFunction, Statistic statistic) {
        this.f = valueFunction::get;
        this.statistic = statistic;
    }

    /**
     * @return Value for the measurement.
     */
    public double getValue() {
        return f.getAsDouble();
    }

    public Statistic getStatistic() {
        return statistic;
    }

    @Override
    public String toString() {
        return "Measurement{" + "statistic='" + statistic + '\'' + ", value=" + getValue() + '}';
    }

}
