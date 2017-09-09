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
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;

import java.util.concurrent.atomic.AtomicBoolean;

public class ReactorMetricsSubscriber<T> extends AtomicBoolean implements Subscription, CoreSubscriber<T> {
    private final String name;
    private final Iterable<Tag> tags;

    public ReactorMetricsSubscriber(String name, Iterable<Tag> tags) {
        this.name = name;
        this.tags = tags;
    }

    @Override
    public void onNext(T t) {

    }

    @Override
    public void onError(Throwable t) {

    }

    @Override
    public void onComplete() {

    }

    @Override
    public void request(long n) {

    }

    @Override
    public void cancel() {

    }

    @Override
    public void onSubscribe(Subscription s) {

    }
}
