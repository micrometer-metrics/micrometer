/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.statsd.internal;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class BufferingFluxTest {

    @Test
    void bufferSingleStrings() {
        Flux<String> source = Flux.just(
                "twelve bytes",
                "fourteen bytes",
                "twelve bytes",
                "fourteen bytes"
        ).delayElements(Duration.ofMillis(50));

        Flux<String> buffered = BufferingFlux.create(source, "\n", 14, 200);

        StepVerifier.create(buffered)
                .expectNext("twelve bytes")
                .expectNext("fourteen bytes")
                .expectNext("twelve bytes")
                .expectNext("fourteen bytes")
                .verifyComplete();
    }

    @Test
    void bufferMultipleStrings() {
        Flux<String> source = Flux.just(
                "twelve bytes",
                "fourteen bytes",
                "twelve bytes",
                "fourteen bytes"
        );

        Flux<String> buffered = BufferingFlux.create(source, "\n", 27, Long.MAX_VALUE);

        StepVerifier.create(buffered)
                .expectNext("twelve bytes\nfourteen bytes")
                .expectNext("twelve bytes\nfourteen bytes")
                .verifyComplete();
    }

    @Test
    void bufferUntilTimeout() {
        Flux<String> source = Flux.concat(
                Mono.just("twelve bytes"),
                Mono.just("fourteen bytes"),
                Mono.just("twelve bytes"),
                Mono.just("fourteen bytes").delayElement(Duration.ofMillis(65)) // avoid multiples of maxMillisecondsBetweenEmits to avoid race condition
        );

        Flux<String> buffered = BufferingFlux.create(source, "\n", Integer.MAX_VALUE, 50);

        StepVerifier.create(buffered)
                .expectNext("twelve bytes\nfourteen bytes\ntwelve bytes")
                .expectNext("fourteen bytes")
                .verifyComplete();
    }

    /**
     * Covers a situation where events were produced at a faster rate than the maxMillisecondsBetweenEmits, and a bug
     * caused it to never emit the events until it reached the maxByteArraySize
     */
    @Test
    void doNotBufferIndefinitely() throws InterruptedException {
        // Produce a value at a more frequent interval than the maxMillisecondsBetweenEmits
        DirectProcessor<Void> end = DirectProcessor.create();
        Flux<String> source = Flux.interval(Duration.ofMillis(100))
            .map(Object::toString);

        Flux<String> buffered = BufferingFlux.create(source, "\n", Integer.MAX_VALUE, 200);

        CountDownLatch received = new CountDownLatch(1);
        buffered.subscribe(v -> received.countDown());

        try {
            received.await(10, TimeUnit.SECONDS);
        } finally {
            end.onComplete();
        }
    }
}