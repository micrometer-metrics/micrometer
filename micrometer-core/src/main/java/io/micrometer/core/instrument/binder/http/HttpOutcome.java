/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.binder.http;

import io.micrometer.common.KeyValue;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.binder.http.HttpObservationDocumentation.CommonLowCardinalityKeys;

class HttpOutcome {

    static final KeyValue HTTP_OUTCOME_SUCCESS = KeyValue.of(CommonLowCardinalityKeys.OUTCOME, "SUCCESS");

    static final KeyValue HTTP_OUTCOME_UNKNOWN = KeyValue.of(CommonLowCardinalityKeys.OUTCOME, "UNKNOWN");

    static KeyValue forStatus(boolean server, @Nullable Integer statusCode) {
        if (statusCode == null) {
            return HTTP_OUTCOME_UNKNOWN;
        }
        if (statusCode >= 200 && statusCode < 300) {
            return HTTP_OUTCOME_SUCCESS;
        }
        else if (!server) {
            Series resolved = Series.resolve(statusCode);
            if (resolved == null) {
                return HTTP_OUTCOME_UNKNOWN;
            }
            return KeyValue.of(CommonLowCardinalityKeys.OUTCOME, resolved.name());
        }
        else {
            return HTTP_OUTCOME_UNKNOWN;
        }
    }

    // Taken from Spring Http
    enum Series {

        INFORMATIONAL(1), SUCCESSFUL(2), REDIRECTION(3), CLIENT_ERROR(4), SERVER_ERROR(5);

        private final int value;

        Series(int value) {
            this.value = value;
        }

        @Nullable
        static Series resolve(int statusCode) {
            int seriesCode = statusCode / 100;
            for (Series series : values()) {
                if (series.value == seriesCode) {
                    return series;
                }
            }
            return null;
        }

    }

}
