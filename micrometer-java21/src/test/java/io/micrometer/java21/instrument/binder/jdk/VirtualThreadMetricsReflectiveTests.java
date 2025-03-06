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
package io.micrometer.java21.instrument.binder.jdk;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

import static java.lang.Thread.State.WAITING;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link VirtualThreadMetrics}. If you run these tests from your IDE,
 * {@link #submitFailedEventsShouldBeRecorded()} might fail depending on your setup. This
 * is because the test (through {@link #virtualThreadFactoryFor(Executor)}) utilizes
 * reflection against the {@code java.lang} package which needs to be explicitly enabled.
 * If you run into such an issue you can either change your setup and let your IDE run the
 * tests utilizing the build system (Gradle) or add the following JVM arg to your test
 * config: {@code --add-opens java.base/java.lang=ALL-UNNAMED}
 *
 * @author Artyom Gabeev
 * @author Jonatan Ivanov
 */
@Tag("reflective")
class VirtualThreadMetricsReflectiveTests {

    private static final Tags TAGS = Tags.of("k", "v");

    private SimpleMeterRegistry registry;

    private VirtualThreadMetrics virtualThreadMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        virtualThreadMetrics = new VirtualThreadMetrics(TAGS);
        virtualThreadMetrics.bindTo(registry);
    }

    @AfterEach
    void tearDown() {
        virtualThreadMetrics.close();
    }

    /**
     * Uses a similar approach as the JDK tests to make starting or unparking a virtual
     * thread fail, see {@link #virtualThreadFactoryFor(Executor)} and <a href=
     * "https://github.com/openjdk/jdk/blob/fdfe503d016086cf78b5a8c27dbe45f0261c68ab/test/jdk/java/lang/Thread/virtual/JfrEvents.java#L143-L187">JfrEvents.java</a>
     */
    @Test
    void submitFailedEventsShouldBeRecorded() {
        try (ExecutorService cachedPool = Executors.newCachedThreadPool()) {
            ThreadFactory factory = virtualThreadFactoryFor(cachedPool);
            Thread thread = factory.newThread(LockSupport::park);
            thread.start();

            await().atMost(Duration.ofSeconds(2)).until(() -> thread.getState() == WAITING);
            cachedPool.shutdown();

            // unpark, the pool was shut down, this should fail
            assertThatThrownBy(() -> LockSupport.unpark(thread)).isInstanceOf(RejectedExecutionException.class);

            Counter counter = registry.get("jvm.threads.virtual.submit.failed").tags(TAGS).counter();
            await().atMost(Duration.ofSeconds(2)).until(() -> counter.count() == 1);

            // park, the pool was shut down, this should fail
            assertThatThrownBy(() -> factory.newThread(LockSupport::park).start())
                .isInstanceOf(RejectedExecutionException.class);
            await().atMost(Duration.ofSeconds(2)).until(() -> counter.count() == 2);
        }
    }

    /**
     * Creates a {@link ThreadFactory} for virtual threads. The created virtual threads
     * will be bound to the provided platform thread pool instead of a default
     * ForkJoinPool. At its current form, this is a hack, it utilizes reflection to supply
     * the platform thread pool. It seems though there is no other way of doing this, the
     * JDK tests are also utilizing reflection to do the same, see: <a href=
     * "https://github.com/openjdk/jdk/blob/fdfe503d016086cf78b5a8c27dbe45f0261c68ab/test/lib/jdk/test/lib/thread/VThreadScheduler.java#L71-L90">VThreadScheduler.java</a>
     * @param pool platform pool
     * @return virtual thread factory bound to the provided platform pool
     */
    private static ThreadFactory virtualThreadFactoryFor(Executor pool) {
        try {
            Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
            Constructor<?> constructor = clazz.getDeclaredConstructor(Executor.class);
            constructor.setAccessible(true);
            return ((Thread.Builder.OfVirtual) constructor.newInstance(pool)).factory();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
