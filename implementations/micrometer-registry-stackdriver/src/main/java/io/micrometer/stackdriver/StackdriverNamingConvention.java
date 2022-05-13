/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.stackdriver;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;

import java.util.regex.Pattern;

/**
 * {@link NamingConvention} for Stackdriver.
 *
 * Names are mapped to Stackdriver's metric type names and tag keys are mapped to its
 * metric label names.
 *
 * @see <a href=
 * "https://cloud.google.com/monitoring/api/v3/metrics-details">"Naming rules" section on
 * Stackdriver's reference documentation</a>
 * @see <a href=
 * "https://cloud.google.com/monitoring/quotas#custom_metrics_quotas">"Custom Metrics" on
 * the Stackdriver's Quotas and limits reference documentation</a>
 * @author Jon Schneider
 * @since 1.1.0
 */
public class StackdriverNamingConvention implements NamingConvention {

    private static final int MAX_NAME_LENGTH = 200;

    private static final int MAX_TAG_KEY_LENGTH = 100;

    private static final int MAX_TAG_VALUE_LENGTH = 1024;

    private static final Pattern NAME_BLACKLIST = Pattern.compile("[^\\w./_]");

    private static final Pattern TAG_KEY_BLACKLIST = Pattern.compile("[^\\w_]");

    private final NamingConvention nameDelegate;

    private final NamingConvention tagKeyDelegate;

    public StackdriverNamingConvention() {
        this(NamingConvention.slashes, NamingConvention.snakeCase);
    }

    public StackdriverNamingConvention(NamingConvention nameDelegate, NamingConvention tagKeyDelegate) {
        this.nameDelegate = nameDelegate;
        this.tagKeyDelegate = tagKeyDelegate;
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return sanitize(nameDelegate.name(name, type, baseUnit), NAME_BLACKLIST, MAX_NAME_LENGTH);
    }

    private String sanitize(String value, Pattern blacklist, int maxLength) {
        return StringUtils.truncate(blacklist.matcher(value).replaceAll("_"), maxLength);
    }

    @Override
    public String tagKey(String key) {
        return sanitize(tagKeyDelegate.tagKey(key), TAG_KEY_BLACKLIST, MAX_TAG_KEY_LENGTH);
    }

    @Override
    public String tagValue(String value) {
        return StringUtils.truncate(value, MAX_TAG_VALUE_LENGTH);
    }

}
