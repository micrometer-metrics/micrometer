/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.statsd.internal;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BufferingFlux}.
 *
 * @author Nils Breunese
 * @author Johnny Lim
 */
class BufferingFluxTest {

    @Test
    void bufferSingleStrings() {
        Flux<String> source = Flux.just("twelve bytes", "fourteen bytes", "twelve bytes", "fourteen bytes")
            .delayElements(Duration.ofMillis(50));

        Flux<String> buffered = BufferingFlux.create(source, "\n", 14, 200);

        StepVerifier.create(buffered)
            .expectNext("twelve bytes\n")
            .expectNext("fourteen bytes\n")
            .expectNext("twelve bytes\n")
            .expectNext("fourteen bytes\n")
            .verifyComplete();
    }

    @Test
    void bufferMultipleStrings() {
        Flux<String> source = Flux.just("twelve bytes", "fourteen bytes", "twelve bytes", "fourteen bytes");

        Flux<String> buffered = BufferingFlux.create(source, "\n", 27, 1000);

        StepVerifier.create(buffered)
            .expectNext("twelve bytes\nfourteen bytes\n")
            .expectNext("twelve bytes\nfourteen bytes\n")
            .verifyComplete();
    }

    @Test
    void bufferUntilTimeout() {
        Flux<String> source = Flux.concat(Mono.just("twelve bytes"), Mono.just("fourteen bytes"),
                Mono.just("twelve bytes"), Mono.just("fourteen bytes").delayElement(Duration.ofMillis(65)) // avoid
                                                                                                           // multiples
                                                                                                           // of
                                                                                                           // maxMillisecondsBetweenEmits
                                                                                                           // to
                                                                                                           // avoid
                                                                                                           // race
                                                                                                           // condition
        );

        Flux<String> buffered = BufferingFlux.create(source, "\n", Integer.MAX_VALUE, 50);

        StepVerifier.create(buffered)
            .expectNext("twelve bytes\nfourteen bytes\ntwelve bytes\n")
            .expectNext("fourteen bytes\n")
            .verifyComplete();
    }

    /**
     * Covers a situation where events were produced at a faster rate than the
     * maxMillisecondsBetweenEmits, and a bug caused it to never emit the events until it
     * reached the maxByteArraySize
     */
    @Test
    void doNotBufferIndefinitely() throws InterruptedException {
        // Produce a value at a more frequent interval than the
        // maxMillisecondsBetweenEmits
        Flux<String> source = Flux.interval(Duration.ofMillis(100)).map(Object::toString);

        Flux<String> buffered = BufferingFlux.create(source, "\n", Integer.MAX_VALUE, 200);

        CountDownLatch received = new CountDownLatch(1);
        buffered.subscribe(v -> received.countDown());

        received.await(10, TimeUnit.SECONDS);
    }

    /**
     * Ensure that we can append buffer messages then split them up without issue.
     *
     * Originally written because buffer messages did not contain a trailing new line so
     * appending messages would join the first line of the new message with the last line
     * of the old message into one line, which is not a valid statsd line.
     */
    @Test
    void bufferMessagesCanBeAppended() throws InterruptedException {
        int numberOfLines = 500;

        List<String> stats = new ArrayList<>();
        IntStream.range(0, numberOfLines).forEachOrdered(i -> stats.add("test.msg.example:" + i + "|c"));

        String[] lines = stats.toArray(new String[0]);
        Flux<String> source = Flux.just(lines).delayElements(Duration.ofMillis(1));
        Flux<String> buffered = BufferingFlux.create(source, "\n", 100, 10);

        CountDownLatch latch = new CountDownLatch(numberOfLines);
        StringBuilder sb = new StringBuilder();
        buffered.subscribe((bufferedLines) -> {
            sb.append(bufferedLines);

            int numberOfBufferedLines = bufferedLines.split("\n").length;
            for (int i = 0; i < numberOfBufferedLines; i++) {
                latch.countDown();
            }
        });
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

        String[] resultLines = sb.toString().split("\n");
        assertThat(resultLines).isEqualTo(lines);
    }

}
