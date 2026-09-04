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
package io.micrometer.core.instrument.distribution;

import io.micrometer.common.lang.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistributionStatisticConfigExtensibilityTest {

    static class SubDistributionStatisticConfig extends DistributionStatisticConfig {

        public static final SubDistributionStatisticConfig NONE = subBuilder().build();

        @Nullable
        protected String extraParam;

        @Nullable
        public String getExtraParam() {
            return extraParam;
        }

        SubDistributionStatisticConfig() {
            super();
        }

        SubDistributionStatisticConfig(DistributionStatisticConfig original) {
            super(original);
            if (original instanceof SubDistributionStatisticConfig) {
                extraParam = ((SubDistributionStatisticConfig) original).extraParam;
            }
        }

        @Override
        public SubDistributionStatisticConfig merge(DistributionStatisticConfig parent) {
            DistributionStatisticConfig mergeBySuper = super.merge(parent);
            SubDistributionStatisticConfig sub = new SubDistributionStatisticConfig(mergeBySuper);
            if (extraParam == null && parent instanceof SubDistributionStatisticConfig) {
                sub.extraParam = ((SubDistributionStatisticConfig) parent).extraParam;
            }
            else {
                sub.extraParam = extraParam;
            }
            return sub;
        }

        public static SubBuilder subBuilder() {
            return new SubBuilder(new SubDistributionStatisticConfig());
        }

        public static class SubBuilder extends Builder {

            protected SubBuilder(SubDistributionStatisticConfig config) {
                super(config);
            }

            public SubBuilder extraParam(@Nullable String extraParam) {
                ((SubDistributionStatisticConfig) config).extraParam = extraParam;
                return this;
            }

            @Override
            protected void validate() {
                super.validate();
                if ("baz".equals(((SubDistributionStatisticConfig) config).extraParam)) {
                    rejectConfig("No bazzing here");
                }
            }

            @Override
            public SubDistributionStatisticConfig build() {
                return (SubDistributionStatisticConfig) super.build();
            }

        }

    }

    @Test
    void mergeFromSuper() {
        DistributionStatisticConfig origin = DistributionStatisticConfig.builder()
            .percentiles(0.5, 0.9)
            .serviceLevelObjectives(1.5, 2.5)
            .percentilePrecision(2)
            .percentilesHistogram(true)
            .build();
        SubDistributionStatisticConfig extra = (SubDistributionStatisticConfig) SubDistributionStatisticConfig
            .subBuilder()
            .extraParam("foo")
            .percentiles(0.5, 0.9, 0.99)
            .build();
        DistributionStatisticConfig merged = origin.merge(extra);
        assertEquals(2, merged.getPercentilePrecision());
        assertEquals(Boolean.TRUE, merged.isPercentileHistogram());
        assertArrayEquals(new double[] { 0.5, 0.9 }, merged.getPercentiles());
        assertFalse(merged instanceof SubDistributionStatisticConfig);
    }

    @Test
    void mergeFromSub() {
        DistributionStatisticConfig origin = DistributionStatisticConfig.builder()
            .percentiles(0.5, 0.9)
            .serviceLevelObjectives(1.5, 2.5)
            .percentilePrecision(2)
            .percentilesHistogram(true)
            .build();
        SubDistributionStatisticConfig extra = (SubDistributionStatisticConfig) SubDistributionStatisticConfig
            .subBuilder()
            .extraParam("foo")
            .percentiles(0.5, 0.9, 0.99)
            .build();
        SubDistributionStatisticConfig merged = extra.merge(origin);
        assertEquals(2, merged.getPercentilePrecision());
        assertEquals(Boolean.TRUE, merged.isPercentileHistogram());
        assertArrayEquals(new double[] { 0.5, 0.9, 0.99 }, merged.getPercentiles());
        assertEquals("foo", merged.getExtraParam());
    }

    @Test
    void mergeSubsTogether() {
        SubDistributionStatisticConfig origin = (SubDistributionStatisticConfig) SubDistributionStatisticConfig
            .subBuilder()
            .extraParam("bar")
            .percentiles(0.5, 0.9)
            .serviceLevelObjectives(1.5, 2.5)
            .percentilePrecision(2)
            .percentilesHistogram(true)
            .build();
        SubDistributionStatisticConfig extra = (SubDistributionStatisticConfig) SubDistributionStatisticConfig
            .subBuilder()
            .extraParam("foo")
            .percentiles(0.5, 0.9, 0.99)
            .build();
        SubDistributionStatisticConfig merged = origin.merge(extra);
        assertEquals(2, merged.getPercentilePrecision());
        assertEquals(Boolean.TRUE, merged.isPercentileHistogram());
        assertArrayEquals(new double[] { 0.5, 0.9 }, merged.getPercentiles());
        assertEquals("bar", merged.getExtraParam());
    }

}
