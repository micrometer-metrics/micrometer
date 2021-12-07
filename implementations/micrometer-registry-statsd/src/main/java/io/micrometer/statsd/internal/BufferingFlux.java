/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.statsd.internal;

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class BufferingFlux {

    private BufferingFlux() {
    }

    /**
     * Creates a Flux that implements Nagle's algorithm to buffer messages -- joined by a delimiter string -- to up a
     * maximum number of bytes, or a maximum duration of time. This avoids sending many small packets in favor of fewer
     * larger ones.
     *
     * @param source                      The input flux.
     * @param delimiter                   The delimiter to use to join messages
     * @param maxByteArraySize            The buffered payload will contain no more than this number of bytes
     * @param maxMillisecondsBetweenEmits Buffered payloads will be emitted no less frequently than this.
     * @return A flux implementing Nagle's algorithm.
     * @see <a href="https://en.wikipedia.org/wiki/Nagle%27s_algorithm">Nagle's algorithm</a>
     */
    public static Flux<String> create(final Flux<String> source, final String delimiter, final int maxByteArraySize, final long maxMillisecondsBetweenEmits) {
        return source.transform(Operators.<String, String>lift((__, s) -> new BufferingSubscriber(
                delimiter,
                maxByteArraySize,
                maxMillisecondsBetweenEmits,
                Schedulers.boundedElastic()
                          .createWorker(),
                s)));
    }


    static class BufferingSubscriber implements CoreSubscriber<String>, Subscription,
                                         Fuseable.QueueSubscription<String>, Runnable {


        final String delimiter;
        final int delimiterSize;
        final int maxByteArraySize;
        final long maxMillisecondsBetweenEmits;

        final Scheduler.Worker worker;
        final CoreSubscriber<? super String> actual;

        Subscription s;
        Disposable disposable;

        int byteSize = 0;
        long lastTime = 0;


        String buffer = "";

        volatile long requested;
        static final AtomicLongFieldUpdater<BufferingSubscriber> REQUESTED =
                AtomicLongFieldUpdater.newUpdater(BufferingSubscriber.class, "requested");

        BufferingSubscriber(
                String delimiter,
                int size,
                long emits,
                Scheduler.Worker worker,
                CoreSubscriber<? super String> actual) {
            this.delimiter = delimiter;
            this.delimiterSize = delimiter.getBytes().length;
            this.maxByteArraySize = size;
            this.maxMillisecondsBetweenEmits = emits;
            this.worker = worker;

            this.actual = actual;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (Operators.validate(this.s, s)) {
                this.s = s;
                this.disposable = this.worker.schedulePeriodically(this,
                        this.maxMillisecondsBetweenEmits,
                        this.maxMillisecondsBetweenEmits,
                        TimeUnit.MILLISECONDS);

                this.actual.onSubscribe(this);
                // Update last time to now if this is the first time
                this.lastTime = System.currentTimeMillis();
                s.request(Long.MAX_VALUE);
            }
        }

        @Override
        public synchronized void onNext(String line) {
            final int bytesLength = line.getBytes().length + delimiterSize;
            final String previousBuffer = this.buffer;
            final int previousBytesSize = this.byteSize;

            final int nextBytesSize = previousBytesSize + bytesLength;
            if (nextBytesSize > this.maxByteArraySize) {
                this.lastTime = System.currentTimeMillis();
                // This creates a buffer, reset size
                this.byteSize = bytesLength;
                this.buffer = line + delimiter; // reset buffer and set this line to the next chunk

                final long requested = this.requested;
                if (requested > 0) {
                    this.actual.onNext(previousBuffer);
                    if (requested != Long.MAX_VALUE) {
                        REQUESTED.decrementAndGet(this);
                    }
                }
            } else {
                this.byteSize = nextBytesSize;
                this.buffer = previousBuffer + line + delimiter;
            }
        }

        @Override
        public void run() {
            this.tryTimeout();
        }

        private synchronized void tryTimeout() {
            final long now = System.currentTimeMillis();
            final long last = lastTime;
            final long diff = now - last;
            if (diff > this.maxMillisecondsBetweenEmits && this.byteSize > 0) {
                final String previousBuffer = this.buffer;
                this.lastTime = now;

                // This creates a buffer, reset size
                this.byteSize = 0;
                this.buffer = ""; // reset buffer and set this line to the next chunk

                final long requested = this.requested;
                if (requested > 0) {
                    this.actual.onNext(previousBuffer);
                    if (requested != Long.MAX_VALUE) {
                        REQUESTED.decrementAndGet(this);
                    }
                }
            }
        }

        @Override
        public synchronized void onError(Throwable t) {
            this.disposable.dispose();
            this.actual.onError(t);
        }

        @Override
        public synchronized void onComplete() {
            this.disposable.dispose();

            final String buffer = this.buffer;
            if (!buffer.isEmpty()) {
                if (this.requested > 0) {
                    this.actual.onNext(buffer);
                }
            }

            this.actual.onComplete();
        }

        @Override
        public void request(long n) {
            if (Operators.validate(n)) {
                Operators.addCap(REQUESTED, this, n);
            }
        }

        @Override
        public void cancel() {
            this.disposable.dispose();
            this.s.cancel();
        }

        @Override
        public int requestFusion(int requestedMode) {
            return Fuseable.NONE;
        }

        @Override
        public String poll() {
            return null;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void clear() {

        }
    }
}
