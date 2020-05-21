/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.influx;

import io.micrometer.core.lang.Nullable;

/**
 * Builds a JSON Bucket representation for InfluxDB 2.
 *
 * @author Jakub Bednar
 */
class CreateBucketJSONBuilder {

    private static final String BUCKET_TEMPLATE = "{\"name\":\"%s\",\"orgID\":\"%s\",\"retentionRules\":[%s]}";
    private static final String RETENTION_RULE_TEMPLATE = "{\"type\":\"expire\",\"everySeconds\":%s}";

    private final String bucket;
    private final String org;

    @Nullable
    private String everySeconds;

    CreateBucketJSONBuilder(String bucket, String org) {
        if (isEmpty(bucket)) {
            throw new IllegalArgumentException("The bucket name cannot be null or empty");
        }
        if (isEmpty(org)) {
            throw new IllegalArgumentException("The org cannot be null or empty");
        }
        this.bucket = bucket;
        this.org = org;
    }

    CreateBucketJSONBuilder setEverySeconds(@Nullable String everySeconds) {

        this.everySeconds = everySeconds;
        return this;
    }

    String build() {

        String retentionRule = everySeconds != null ? String.format(RETENTION_RULE_TEMPLATE, everySeconds) : "";

        return String.format(BUCKET_TEMPLATE, bucket, org, retentionRule);
    }

    private boolean isEmpty(@Nullable String string) {
        return string == null || string.isEmpty();
    }
}
