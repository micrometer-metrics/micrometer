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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.LL_Result;
import org.openjdk.jcstress.infra.results.L_Result;
import org.openjdk.jcstress.infra.results.Z_Result;

public class MeterRegistryConcurrencyTest {

    /*
     * Registering the same new Meter from multiple threads should be safe and consistent.
     */
    @JCStressTest
    @Outcome(id = { "true" }, expect = Expect.ACCEPTABLE, desc = "same meter returned for concurrent registers")
    @Outcome(expect = Expect.FORBIDDEN)
    @State
    public static class ConcurrentRegisterNew {

        MeterRegistry registry = new SimpleMeterRegistry();

        Counter c1;

        Counter c2;

        @Actor
        public void actor1() {
            c1 = registry.counter("counter");
        }

        @Actor
        public void actor2() {
            c2 = registry.counter("counter");
        }

        @Arbiter
        public void arbiter(Z_Result r) {
            r.r1 = c1 == c2;
        }

    }

    /*
     * Likewise, registering an existing Meter from multiple threads is safe.
     */
    @JCStressTest
    @Outcome(id = { "true" }, expect = Expect.ACCEPTABLE, desc = "same meter returned for concurrent registers")
    @Outcome(expect = Expect.FORBIDDEN)
    @State
    public static class ConcurrentRegisterExisting {

        MeterRegistry registry = new SimpleMeterRegistry();

        Counter c1;

        Counter c2;

        public ConcurrentRegisterExisting() {
            registry.counter("counter");
        }

        @Actor
        public void actor1() {
            c1 = registry.counter("counter");
        }

        @Actor
        public void actor2() {
            c2 = registry.counter("counter");
        }

        @Arbiter
        public void arbiter(Z_Result r) {
            r.r1 = c1 == c2;
        }

    }

    // @formatter:off
    /*
      When configuring a MeterFilter after a Meter has already been registered, existing meters will be marked stale.
      Subsequent calls to {@code getOrCreateMeter} for those Meters create a new Meter with all MeterFilters applied.
      If multiple concurrent calls to {@code getOrCreateMeter} interleave, it's possible not all see the new Meter.
      We ideally want both to get the new meter, but we don't want to pay the cost associated with that level of safety
      given the expected rarity of this situation happening, so we aim to get as close as possible for cheap.

            RESULT     SAMPLES     FREQ       EXPECT  DESCRIPTION
        null, null           0    0.00%    Forbidden  both get stale meter
         null, tag      39,491    0.13%  Interesting  one stale meter returned
         tag, null      40,389    0.13%  Interesting  one stale meter returned
          tag, tag  30,941,328   99.74%   Acceptable  both get new meter
     */
    // @formatter:on
    @JCStressTest
    @Outcome(id = { "tag, tag" }, expect = Expect.ACCEPTABLE, desc = "both get new meter")
    @Outcome(id = { "null, tag", "tag, null" }, expect = Expect.ACCEPTABLE_INTERESTING,
            desc = "one stale meter returned")
    @Outcome(id = { "null, null" }, expect = Expect.FORBIDDEN, desc = "both get stale meter")
    @State
    public static class ConcurrentRegisterWithStaleId {

        MeterRegistry registry = new SimpleMeterRegistry();

        Counter c1;

        Counter c2;

        public ConcurrentRegisterWithStaleId() {
            registry.counter("counter");
            registry.counter("another");
            registry.config().commonTags("common", "tag");
        }

        @Actor
        public void actor1() {
            c1 = registry.counter("counter");
        }

        @Actor
        public void actor2() {
            c2 = registry.counter("counter");
        }

        @Arbiter
        public void arbiter(LL_Result r) {
            r.r1 = c1.getId().getTag("common");
            r.r2 = c2.getId().getTag("common");
        }

    }

    /*
     * Verify the fix for a ConcurrentModificationException that was being thrown in the
     * tested scenario. The late registration of a MeterFilter causes iteration of the
     * KeySet of the preFilterIdToMeterMap to add them to the stalePreFilterIds set. This
     * iteration could happen at the same time a new meter is being registered, thus added
     * to the preFilterIdToMeterMap, modifying it while iterating over its KeySet.
     */
    // @Issue("gh-5489")
    @JCStressTest
    @Outcome(id = "OK", expect = Expect.ACCEPTABLE, desc = "No exception")
    @Outcome(expect = Expect.FORBIDDEN, desc = "Exception thrown")
    @State
    public static class ConfigureLateMeterFilterWithNewMeterRegister {

        MeterRegistry registry = new SimpleMeterRegistry();

        public ConfigureLateMeterFilterWithNewMeterRegister() {
            // registry not empty so the MeterFilter is treated as late configuration
            registry.counter("c1");
        }

        @Actor
        public void actor1() {
            // adds a new meter to preMap, a concurrent modification if not guarded
            registry.counter("c2");
        }

        @Actor
        public void actor2(L_Result r) {
            try {
                // iterates over preMap keys to add all to the staleIds
                registry.config().commonTags("common", "tag");
                r.r1 = "OK";
            }
            catch (Throwable e) {
                r.r1 = e.getClass().getSimpleName();
            }
        }

    }

}
