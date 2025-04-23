/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.benchmark;

import io.micrometer.common.lang.Nullable;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.LinuxPerfAsmProfiler;
import org.openjdk.jmh.profile.LinuxPerfNormProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class BenchmarkSupport {

    // Making things deterministic
    private static final Random RANDOM = new Random(0x5EAL);

    // A generic number of test instances to use. Making sure it's small
    // enough for L2 even for moderately sized objects, yet big enough
    // to confuse JIT.
    public static final int DEFAULT_POOL_SIZE = 1 << 12;

    // A bitmask that would prevent an incremented number escaping
    // DEFAULT_POOL_SIZE by a simple AND operation.
    public static final int DEFAULT_MASK = DEFAULT_POOL_SIZE - 1;

    // To prevent any kind of JIT assumption that a specific instance
    // corresponds to a specific sample (shouldn't happen, but anyway),
    // we need to walk them at a different rate. 2 is a bad choice,
    // because it will discard half of the samples, but for every
    // n = 2^k the value of 3 will be a divisor of either n + 1 or
    // 2n + 1.
    public static final int SAMPLE_STEP = 3;

    private BenchmarkSupport() {
    }

    /**
     * When you need to break existing patterns in an array.
     */
    public static <T> T[] shuffle(T[] values) {
        for (int i = 0; i < values.length; i++) {
            int target = RANDOM.nextInt(values.length);
            T buffer = values[target];
            values[target] = values[i];
            values[i] = buffer;
        }

        return values;
    }

    /**
     * Create a bit mask with [count] of [options] bits set. A simple method to select a
     * combination of unique items using each bit as the presence mark for every item.
     * @param count Number of bits to be set.
     * @param options Number of all possible options.
     */
    public static BitSet selection(int count, int options) {
        if (count > options) {
            throw new IllegalArgumentException(
                    "Requested count " + count + " is bigger than list of available options (" + options + ")");
        }

        if (count < 0) {
            throw new IllegalArgumentException(
                    "Number of selected options must be non-negative, " + count + " provided");
        }

        BitSet reply = new BitSet(options);

        if (count == options) {
            for (int i = 0; i < count; i++) {
                reply.set(i);
            }
            return reply;
        }

        for (int flag = 0; flag < count; flag++) {
            // with [remaining] positions left, we select a _disabled_
            // bit N to install, meaning we have to pretend that all
            // _enabled_ bits don't exist at all.

            int remaining = options - flag;
            int next = RANDOM.nextInt(remaining);
            int skipped = 0;

            for (int bit = 0; bit < options; bit++) {
                if (reply.get(bit)) {
                    skipped++;
                    continue;
                }

                if (bit == skipped + next) {
                    reply.set(bit);
                    break;
                }
            }

            // bit can be as much as options - 1 in the previous loop, thus >=
            if (skipped + next >= options) {
                String message = String.format(
                        "Failed to set the bit: while looking to set option #%d out of total %d, disabled bit #%d should have been enabled, however, %d bits were skipped in %s",
                        flag, options, next, skipped, reply);
                throw new IllegalStateException(message);
            }
        }

        return reply;
    }

    public static BitSet selection(int minimum, int maximum, int options) {
        int count = minimum + RANDOM.nextInt(maximum - minimum + 1);
        return selection(count, options);
    }

    public static BitSet selection(int options) {
        return selection(RANDOM.nextInt(options + 1), options);
    }

    public static class ModeUniformDistribution {

        private ModeUniformDistribution() {
        }

        /**
         * <p>
         * A silly mock distribution to test the reaction of the code to different input
         * sizes. With the specified probability, the [mode] value will be returned,
         * otherwise a value between [minimum] and [maximum] (inclusively, but without the
         * mode) will be selected uniformly.
         * </p>
         *
         * <p>
         * The reason for not taking any nonsilly distribution is the generic case modeled
         * here, a case with a distinct mode, but without the probability of other values
         * falling off sharply with the distance from the mode. This allows both to see
         * how code reacts to the position of that mode and to confuse the JIT regarding
         * any assumptions (beyond mode value) and machinery like predictors. Using just a
         * bell-like distribution here would either shrink the mode or reduce output to
         * 3-5 values that would totally dominate everything else, so it requires a tight
         * bell mixed with a uniform distribution to blend in all possibilities. This is
         * exactly what this method does, with replacing the distribution by explicit
         * boost for a specific value to keep things less error-prone (no one ever will
         * plot these values or look at them in the debugger again).
         * </p>
         * @param minimum The minimum amount that can be selected, inclusive.
         * @param maximum The maximum amount that can be selected, inclusive.
         * @param mode The value that appears with increased probability.
         * @param probability The probability of selecting the value instead of using
         * uniform distribution.
         * @return Value that differs from uniform distribution by a more often selected
         * mode, according to passed probability.
         */
        public static int sample(int minimum, int maximum, int mode, double probability) {
            if (mode < minimum || mode > maximum) {
                throw new IllegalArgumentException(
                        "Provided mode " + mode + " isn't in the min/max interval [" + minimum + ", " + maximum + "]");
            }

            if (RANDOM.nextDouble() < probability) {
                return mode;
            }

            // excluding mode here, so upper bound is maximum, not maximum + 1
            int selection = minimum + RANDOM.nextInt(maximum - minimum);

            // compensating for absent mode
            if (selection >= mode) {
                return selection + 1;
            }

            return selection;
        }

    }

    public static ChainedOptionsBuilder defaults() {
        ChainedOptionsBuilder options = new OptionsBuilder().forks(1)
            // 0.5m warmups
            .warmupIterations(6)
            .warmupTime(new TimeValue(5L, TimeUnit.SECONDS))
            // 9.5m benchmarks
            .measurementIterations(57)
            .measurementTime(new TimeValue(5L, TimeUnit.SECONDS))
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .shouldDoGC(true)
            .addProfiler(GCProfiler.class);

        // Please forgive me
        if (System.getProperty("os.name", "_fallback_").toLowerCase(Locale.ROOT).contains("linux")) {
            options.addProfiler(LinuxPerfAsmProfiler.class).addProfiler(LinuxPerfNormProfiler.class);
        }

        return options;
    }

    /**
     * Runs benchmarks in specified classes with defaults (10s iterations, 0.5m warmup,
     * 9.5m benchmark, GC & perf profilers). As usual, be aware that defaults might not
     * suit your case, use overrides when necessary.
     */
    public static void run(String[] patterns, @Nullable ChainedOptionsBuilder override) throws RunnerException {
        ChainedOptionsBuilder options = defaults();

        for (String pattern : patterns) {
            options.include(pattern);
        }

        Options defaults = options.build();

        new Runner(override == null ? defaults : override.parent(defaults).build()).run();
    }

    /**
     * @see #run(String[], ChainedOptionsBuilder)
     */
    public static void run(String[] patterns) throws RunnerException {
        run(patterns, null);
    }

    /**
     * @see #run(String[], ChainedOptionsBuilder)
     */
    public static void run(Class<?>[] sources, @Nullable ChainedOptionsBuilder override) throws RunnerException {
        String[] patterns = Arrays.stream(sources).map(Class::getCanonicalName).toArray(String[]::new);

        run(patterns, override);
    }

    /**
     * @see #run(String[], ChainedOptionsBuilder)
     */
    public static void run(Class<?> source, @Nullable ChainedOptionsBuilder override) throws RunnerException {
        run(new Class<?>[] { source }, override);
    }

    /**
     * @see #run(String[], ChainedOptionsBuilder)
     */
    public static void run(Class<?>... sources) throws RunnerException {
        run(sources, null);
    }

    /**
     * @see #run(String[], ChainedOptionsBuilder)
     */
    public static void run(Class<?> source) throws RunnerException {
        run(source, null);
    }

    public static void main(String[] includes) throws RunnerException {
        ChainedOptionsBuilder builder = defaults()
            // Only 2m benchmarks, we're launching everything at once here
            .measurementIterations(24)
            .measurementTime(new TimeValue(5L, TimeUnit.SECONDS));

        for (String pattern : includes) {
            builder.include(pattern);
        }

        Options options = builder.build();

        Runner runner = new Runner(options);

        if (includes.length == 0) {
            System.out.println("Specify benchmark patterns as CLI arguments");
            runner.list();
        }
        else {
            runner.run();
        }
    }

}
