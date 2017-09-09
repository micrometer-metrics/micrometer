/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.reactor;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.reactivestreams.Publisher;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.core.publisher.Operators;

import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

/**
 * @author Jon Schneider
 */
public class ReactorMetrics {
    private ReactorMetrics() {
    }

    /**
     * @param <T> an arbitrary type that is left unchanged by the metrics operator
     * @return a new metrics-recording operator pointcut
     */
    public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> timed(Iterable<Tag> tags) {
        return Operators.lift(POINTCUT_FILTER, ((scannable, sub) -> {
            //do not time fused flows
            if (scannable instanceof Fuseable && sub instanceof Fuseable.QueueSubscription) {
                return sub;
            }
            return new ReactorMetricsSubscriber<>(
                scannable.name(),
                Tags.concat(tags, scannable.tags().map(t -> Tag.of(t.getT1(), t.getT2())).collect(toList()))
            );
        }));
    }

    private static final Predicate<Scannable> POINTCUT_FILTER =
        s -> !(s instanceof Fuseable.ScalarCallable);
}