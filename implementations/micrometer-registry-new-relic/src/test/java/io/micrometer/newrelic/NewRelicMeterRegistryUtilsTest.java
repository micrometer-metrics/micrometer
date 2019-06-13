/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.newrelic;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Denis Tazhkenov
 */
class NewRelicMeterRegistryUtilsTest {

    private static class Checker<T> implements Consumer<List<T>> {
        final int[] batches;
        int position = 0;

        Checker(int[] batches) {
            this.batches = batches;
        }

        @Override
        public void accept(List<T> t) {
            System.out.println(t.size() + " " + t);
            assertEquals(batches[position], t.size());
            position += 1;
        }

        void checkComplete() {
            assertEquals(batches.length, position);
        }
    }

    @Test
    void sendInBatches() {
        Checker<String> oneChecker = new Checker<>(new int[]{1});
        NewRelicMeterRegistry.sendInBatches(2, Collections.singletonList("0"), oneChecker);
        oneChecker.checkComplete();

        Checker<String> evenChecker = new Checker<>(new int[]{2, 2});
        NewRelicMeterRegistry.sendInBatches(2, Arrays.asList("0", "1", "2", "3"), evenChecker);
        evenChecker.checkComplete();

        Checker<String> oddChecker = new Checker<>(new int[]{2, 1});
        NewRelicMeterRegistry.sendInBatches(2, Arrays.asList("0", "1", "2"), oddChecker);
        oddChecker.checkComplete();

        Checker<String> emptyChecker = new Checker<>(new int[]{});
        NewRelicMeterRegistry.sendInBatches(2, Collections.emptyList(), emptyChecker);
        emptyChecker.checkComplete();
    }
}
