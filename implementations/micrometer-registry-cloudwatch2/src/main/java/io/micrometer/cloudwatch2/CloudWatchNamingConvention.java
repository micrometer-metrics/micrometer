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
package io.micrometer.cloudwatch2;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;

/**
 * {@link NamingConvention} for CloudWatch.
 *
 * @author Klaus Hartl
 * @since 1.3.6
 */
public class CloudWatchNamingConvention implements NamingConvention {

    // https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_Dimension.html
    private static final int MAX_TAG_KEY_LENGTH = 255;

    private static final int MAX_TAG_VALUE_LENGTH = 1024;

    private final NamingConvention delegate;

    public CloudWatchNamingConvention() {
        this(NamingConvention.identity);
    }

    public CloudWatchNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return delegate.name(name, type, baseUnit);
    }

    @Override
    public String tagKey(String key) {
        return StringUtils.truncate(delegate.tagKey(key), MAX_TAG_KEY_LENGTH);
    }

    @Override
    public String tagValue(String value) {
        return StringUtils.truncate(delegate.tagValue(value), MAX_TAG_VALUE_LENGTH);
    }

}
