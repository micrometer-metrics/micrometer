/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.health;

import java.util.function.BinaryOperator;

/**
 * Utilities for queries.
 *
 * @author Jon Schneider
 */
class QueryUtils {

    static final BinaryOperator<Double> SUM_OR_NAN = (v1, v2) -> {
        if (Double.isNaN(v1)) {
            return v2;
        }
        else if (Double.isNaN(v2)) {
            return v1;
        }
        return v1 + v2;
    };

    static final BinaryOperator<Double> MAX_OR_NAN = (v1, v2) -> {
        if (Double.isNaN(v1)) {
            return v2;
        }
        else if (Double.isNaN(v2)) {
            return v1;
        }
        return Math.max(v1, v2);
    };

    private QueryUtils() {
    }

}
