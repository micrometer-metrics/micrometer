/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.concurrencytests;

import static org.openjdk.jcstress.annotations.Expect.*;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.*;
import io.micrometer.registry.otlp.internal.Base2ExponentialHistogram;
import io.micrometer.registry.otlp.internal.CumulativeBase2ExponentialHistogram;

public class Base2ExponentialHistogramConcurrencyTests {

    @JCStressTest
    @Outcome(id = "null, 5, 2", expect = ACCEPTABLE, desc = "Read after all writes")
    @Outcome(id = "null, 20, 0", expect = ACCEPTABLE, desc = "Read before write")
    @Outcome(id = { "null, 20, 1" }, expect = ACCEPTABLE, desc = "Reading after single " + "write")
    @Outcome(
            id = { "class java.lang.ArrayIndexOutOfBoundsException, 20, 0",
                    "class java.lang.ArrayIndexOutOfBoundsException, 20, 1" },
            expect = FORBIDDEN, desc = "Exception in recording thread")
    @Outcome(
            id = "class java.lang.ArrayIndexOutOfBoundsException, class java.lang.ArrayIndexOutOfBoundsException, null",
            expect = FORBIDDEN, desc = "Exception in both reading and writing threads")
    @Outcome(id = "null, class java.lang.ArrayIndexOutOfBoundsException, null", expect = FORBIDDEN,
            desc = "Exception in reading thread")
    @Outcome(expect = UNKNOWN)
    @State
    public static class RescalingAndConcurrentReading {

        Base2ExponentialHistogram exponentialHistogram = new CumulativeBase2ExponentialHistogram(20, 40, 0, null);

        @Actor
        public void actor1(LLL_Result r) {
            try {
                exponentialHistogram.recordDouble(2);
            }
            catch (Exception e) {
                r.r1 = e.getClass();
            }
        }

        @Actor
        public void actor2(LLL_Result r) {
            try {
                exponentialHistogram.recordDouble(4);
            }
            catch (Exception e) {
                r.r1 = e.getClass();
            }
        }

        @Actor
        public void actor3(LLL_Result r) {
            try {
                exponentialHistogram.takeSnapshot(2, 6, 4);
                r.r2 = exponentialHistogram.getLatestExponentialHistogramSnapshot().scale();
                r.r3 = exponentialHistogram.getLatestExponentialHistogramSnapshot()
                    .positive()
                    .bucketCounts()
                    .stream()
                    .mapToLong(Long::longValue)
                    .sum();
            }
            catch (Exception e) {
                r.r2 = e.getClass();
            }
        }

    }

}
