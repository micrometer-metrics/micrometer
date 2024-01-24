/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.core.instrument.kotlin;

import io.micrometer.common.lang.Nullable;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.ThreadContextElement;

/**
 * {@link ThreadContextElement} for synchronizing a {@link ContextSnapshot.Scope} across
 * coroutine suspension and resumption.
 */
class KotlinObservationContextElement implements ThreadContextElement<ContextSnapshot.Scope> {

    static final CoroutineContext.Key<KotlinObservationContextElement> KEY = new CoroutineContext.Key<KotlinObservationContextElement>() {
    };

    private final ObservationRegistry observationRegistry;

    private final ContextSnapshot contextSnapshot;

    KotlinObservationContextElement(ObservationRegistry observationRegistry, ContextRegistry contextRegistry) {
        this.observationRegistry = observationRegistry;
        this.contextSnapshot = ContextSnapshotFactory.builder()
            .captureKeyPredicate(ObservationThreadLocalAccessor.KEY::equals)
            .contextRegistry(contextRegistry)
            .build()
            .captureAll();
    }

    @Override
    public Key<?> getKey() {
        return KEY;
    }

    Observation getCurrentObservation() {
        return this.observationRegistry.getCurrentObservation();
    }

    @Override
    @SuppressWarnings("MustBeClosedChecker")
    public ContextSnapshot.Scope updateThreadContext(CoroutineContext coroutineContext) {
        return this.contextSnapshot.setThreadLocals(ObservationThreadLocalAccessor.KEY::equals);
    }

    @Override
    public void restoreThreadContext(CoroutineContext coroutineContext, ContextSnapshot.Scope scope) {
        scope.close();
    }

    @Override
    public CoroutineContext plus(CoroutineContext coroutineContext) {
        return CoroutineContext.DefaultImpls.plus(this, coroutineContext);
    }

    @Override
    public <R> R fold(R initial, Function2<? super R, ? super Element, ? extends R> operation) {
        return Element.DefaultImpls.fold(this, initial, operation);
    }

    @Nullable
    @Override
    public <E extends Element> E get(Key<E> key) {
        return Element.DefaultImpls.get(this, key);
    }

    @Override
    public CoroutineContext minusKey(Key<?> key) {
        return Element.DefaultImpls.minusKey(this, key);
    }

}
