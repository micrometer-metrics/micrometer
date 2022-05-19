/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.micrometer.conventions.common;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AttributeKeyTests {

    @Test
    void nullToEmpty() {
        Assertions.assertThat(AttributeKey.stringKey(null).getKey()).isEmpty();
    }

}
