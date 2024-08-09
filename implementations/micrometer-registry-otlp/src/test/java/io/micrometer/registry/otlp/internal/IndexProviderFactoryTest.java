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
package io.micrometer.registry.otlp.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IndexProviderFactoryTest {

    @Test
    void testIndexProviderCache() {
        assertThat(IndexProviderFactory.getIndexProviderForScale(0))
            .isEqualTo(IndexProviderFactory.getIndexProviderForScale(0));
        assertThat(IndexProviderFactory.getIndexProviderForScale(1))
            .isEqualTo(IndexProviderFactory.getIndexProviderForScale(1));
        assertThat(IndexProviderFactory.getIndexProviderForScale(-1))
            .isEqualTo(IndexProviderFactory.getIndexProviderForScale(-1));
    }

    @Test
    void testGetIndexForValueForZeroScale() {
        IndexProvider indexProvider = IndexProviderFactory.getIndexProviderForScale(0);
        assertThat(indexProvider.getIndexForValue(1)).isEqualTo(-1);
        assertThat(indexProvider.getIndexForValue(1.5)).isZero();
        assertThat(indexProvider.getIndexForValue(2)).isZero();

        assertThat(indexProvider.getIndexForValue(Math.pow(2, 1023))).isEqualTo(1022);
        assertThat(indexProvider.getIndexForValue(Double.MAX_VALUE)).isEqualTo(1023);

        assertThat(indexProvider.getIndexForValue(Math.pow(2, -1021))).isEqualTo(-1022);
        assertThat(indexProvider.getIndexForValue(Double.MIN_VALUE)).isEqualTo(-1075);
    }

    @Test
    void testGetIndexForValueForPositiveScale() {
        IndexProvider indexProvider = IndexProviderFactory.getIndexProviderForScale(1);
        assertThat(indexProvider.getIndexForValue(1)).isEqualTo(-1);
        assertThat(indexProvider.getIndexForValue(1.4)).isZero();
        assertThat(indexProvider.getIndexForValue(2)).isEqualTo(1);

        double tmp = (Math.pow(2, 1023) - Math.pow(2, 1022)) / 1.99;
        assertThat(indexProvider.getIndexForValue(Math.pow(2, 1023) + tmp)).isEqualTo(2046);
        assertThat(indexProvider.getIndexForValue(Double.MAX_VALUE)).isEqualTo(2047);

        assertThat(indexProvider.getIndexForValue(Double.MIN_VALUE)).isEqualTo(-2149);
    }

    @Test
    void testGetIndexForNegativeScale() {
        IndexProvider indexProvider = IndexProviderFactory.getIndexProviderForScale(-1);
        assertThat(indexProvider.getIndexForValue(1)).isEqualTo(-1);
        assertThat(indexProvider.getIndexForValue(4)).isZero();
        assertThat(indexProvider.getIndexForValue(4.1)).isEqualTo(1);

        assertThat(indexProvider.getIndexForValue(Math.pow(2, 1021))).isEqualTo(510);
        assertThat(indexProvider.getIndexForValue(Double.MAX_VALUE)).isEqualTo(511);

        assertThat(indexProvider.getIndexForValue(Double.MIN_NORMAL)).isEqualTo(-512);
        assertThat(indexProvider.getIndexForValue(Double.MIN_VALUE)).isEqualTo(-538);
    }

}
