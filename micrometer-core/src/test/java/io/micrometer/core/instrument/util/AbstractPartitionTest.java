/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.micrometer.core.instrument.util.AbstractPartition.partitionCount;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractPartitionTest {

    @Test
    void nullNotAllowed() {
        assertThatThrownBy(() -> new TestPartition(null, 1)).hasMessage("delegate == null");
    }

    @Test
    void negativeLengthPartitionInvalid() {
        assertThatThrownBy(() -> new TestPartition(new ArrayList<>(), -1)).hasMessage("partitionSize < 1");
    }

    @Test
    void zeroLengthPartitionInvalid() {
        assertThatThrownBy(() -> new TestPartition(new ArrayList<>(), 0)).hasMessage("partitionSize < 1");
    }

    // Factory methods could have avoided even instantiating a wrapper in this case, but
    // since code
    // extends this, we test things work sensibly.
    @Test
    void singlePartitionListActsLikeDelegate_empty() {
        TestPartition singlePartition = new TestPartition(new ArrayList<>(), 1);

        assertThat(singlePartition).isEmpty();
        assertThat(singlePartition.get(0)).isEmpty();
    }

    @Test
    void singlePartitionListActsLikeDelegate_singleElement() {
        TestPartition singlePartition = new TestPartition(asList("foo"), 1);

        assertThat(singlePartition).hasSize(1);
        assertThat(singlePartition.get(0)).containsExactly("foo");
    }

    @Test
    void singlePartitionListActsLikeDelegate_multipleElements() {
        TestPartition singlePartition = new TestPartition(asList("foo", "bar"), 2);

        assertThat(singlePartition).hasSize(1);
        assertThat(singlePartition.get(0)).containsExactly("foo", "bar");
    }

    @Test
    void lessElementsThanPartitionSize() {
        TestPartition partition = new TestPartition(asList("foo", "bar"), 3);

        assertThat(partition).hasSize(1);
        assertThat(partition.get(0)).containsExactly("foo", "bar");
    }

    @Test
    void partitionCount_roundsUp() {
        assertThat(partitionCount(asList("foo", "bar", "baz"), 2)).isEqualTo(2);
    }

    @Test
    void moreElementsThanPartitionSize() {
        TestPartition partition = new TestPartition(asList("foo", "bar", "baz"), 2);

        assertThat(partition.size()).isEqualTo(2);
        assertThat(partition.get(0)).containsExactly("foo", "bar");
        assertThat(partition.get(1)).containsExactly("baz");
    }

    static class TestPartition extends AbstractPartition<String> {

        TestPartition(List<String> delegate, int partitionSize) {
            super(delegate, partitionSize);
        }

    }

}
