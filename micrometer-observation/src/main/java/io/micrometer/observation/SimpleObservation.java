/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link Observation}.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @author Yanming Zhou
 * @since 1.10.0
 */
class SimpleObservation implements Observation {

    final ObservationRegistry registry;

    private final Context context;

    @Nullable
    @SuppressWarnings("rawtypes")
    private ObservationConvention convention;

    @SuppressWarnings("rawtypes")
    private final Deque<ObservationHandler> handlers;

    private final Collection<ObservationFilter> filters;

    final Map<Thread, Scope> lastScope = new ConcurrentHashMap<>();

    SimpleObservation(@Nullable String name, ObservationRegistry registry, Context context) {
        this.registry = registry;
        this.context = context;
        this.context.setName(name);
        this.convention = getConventionFromConfig(registry, context);
        this.handlers = getHandlersFromConfig(registry, context);
        this.filters = registry.observationConfig().getObservationFilters();
    }

    SimpleObservation(ObservationConvention<? extends Context> convention, ObservationRegistry registry,
            Context context) {
        this.registry = registry;
        this.context = context;
        // name is set later in start()
        this.handlers = getHandlersFromConfig(registry, context);
        this.filters = registry.observationConfig().getObservationFilters();
        if (convention.supportsContext(context)) {
            this.convention = convention;
        }
        else {
            throw new IllegalStateException(
                    "Convention [" + convention + "] doesn't support context [" + context + "]");
        }
    }

    @Nullable
    private static ObservationConvention getConventionFromConfig(ObservationRegistry registry, Context context) {
        for (ObservationConvention<?> convention : registry.observationConfig().getObservationConventions()) {
            if (convention.supportsContext(context)) {
                return convention;
            }
        }
        return null;
    }

    private static Deque<ObservationHandler> getHandlersFromConfig(ObservationRegistry registry, Context context) {
        Collection<ObservationHandler<?>> handlers = registry.observationConfig().getObservationHandlers();
        Deque<ObservationHandler> deque = new ArrayDeque<>(handlers.size());
        for (ObservationHandler handler : handlers) {
            if (handler.supportsContext(context)) {
                deque.add(handler);
            }
        }
        return deque;
    }

    @Override
    public Observation contextualName(@Nullable String contextualName) {
        this.context.setContextualName(contextualName);
        return this;
    }

    @Override
    public Observation parentObservation(@Nullable Observation parentObservation) {
        this.context.setParentObservation(parentObservation);
        return this;
    }

    @Override
    public Observation lowCardinalityKeyValue(KeyValue keyValue) {
        this.context.addLowCardinalityKeyValue(keyValue);
        return this;
    }

    @Override
    public Observation highCardinalityKeyValue(KeyValue keyValue) {
        this.context.addHighCardinalityKeyValue(keyValue);
        return this;
    }

    @Override
    public Observation observationConvention(ObservationConvention<?> convention) {
        if (convention.supportsContext(context)) {
            this.convention = convention;
        }
        return this;
    }

    @Override
    public Observation error(Throwable error) {
        this.context.setError(error);
        notifyOnError();
        return this;
    }

    @Override
    public Observation event(Event event) {
        notifyOnEvent(event);
        return this;
    }

    @Override
    public Observation start() {
        if (this.convention != null) {
            this.context.addLowCardinalityKeyValues(convention.getLowCardinalityKeyValues(context));
            this.context.addHighCardinalityKeyValues(convention.getHighCardinalityKeyValues(context));

            String newName = convention.getName();
            if (StringUtils.isNotBlank(newName)) {
                this.context.setName(newName);
            }
        }

        notifyOnObservationStarted();
        return this;
    }

    @Override
    public Context getContext() {
        return this.context;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void stop() {
        if (this.convention != null) {
            this.context.addLowCardinalityKeyValues(convention.getLowCardinalityKeyValues(context));
            this.context.addHighCardinalityKeyValues(convention.getHighCardinalityKeyValues(context));

            String newContextualName = convention.getContextualName(context);
            if (StringUtils.isNotBlank(newContextualName)) {
                this.context.setContextualName(newContextualName);
            }
        }

        Context modifiedContext = this.context;
        for (ObservationFilter filter : this.filters) {
            modifiedContext = filter.map(modifiedContext);
        }

        notifyOnObservationStopped(modifiedContext);
    }

    @Override
    public Scope openScope() {
        Scope scope = new SimpleScope(this.registry, this);
        notifyOnScopeOpened();
        lastScope.put(Thread.currentThread(), scope);
        return scope;
    }

    @Nullable
    @Override
    public Scope getEnclosingScope() {
        return lastScope.get(Thread.currentThread());
    }

    @Override
    public String toString() {
        return "{" + "name=" + this.context.getName() + "(" + this.context.getContextualName() + ")" + ", error="
                + this.context.getError() + ", context=" + this.context + '}';
    }

    @SuppressWarnings("unchecked")
    void notifyOnObservationStarted() {
        for (ObservationHandler handler : this.handlers) {
            handler.onStart(this.context);
        }
    }

    @SuppressWarnings("unchecked")
    void notifyOnError() {
        for (ObservationHandler handler : this.handlers) {
            handler.onError(this.context);
        }
    }

    @SuppressWarnings("unchecked")
    void notifyOnEvent(Event event) {
        for (ObservationHandler handler : this.handlers) {
            handler.onEvent(event, this.context);
        }
    }

    @SuppressWarnings("unchecked")
    void notifyOnScopeOpened() {
        for (ObservationHandler handler : this.handlers) {
            handler.onScopeOpened(this.context);
        }
    }

    @SuppressWarnings("unchecked")
    void notifyOnScopeClosed() {
        // We're closing from end till the beginning - e.g. we opened scope with handlers
        // with ids 1,2,3 and we need to close the scope in order 3,2,1
        Iterator<ObservationHandler> iterator = this.handlers.descendingIterator();
        while (iterator.hasNext()) {
            ObservationHandler handler = iterator.next();
            handler.onScopeClosed(this.context);
        }
    }

    @SuppressWarnings("unchecked")
    void notifyOnScopeMakeCurrent() {
        for (ObservationHandler handler : this.handlers) {
            handler.onScopeOpened(this.context);
        }
    }

    @SuppressWarnings("unchecked")
    void notifyOnScopeReset() {
        for (ObservationHandler handler : this.handlers) {
            handler.onScopeReset(this.context);
        }
    }

    @SuppressWarnings("unchecked")
    void notifyOnObservationStopped(Context context) {
        // We're closing from end till the beginning - e.g. we started with handlers with
        // ids 1,2,3 and we need to call close on 3,2,1
        this.handlers.descendingIterator().forEachRemaining(handler -> handler.onStop(context));
    }

    @Override
    public ObservationRegistry getObservationRegistry() {
        return this.registry;
    }

    static class SimpleScope implements Scope {

        private static final InternalLogger log = InternalLoggerFactory.getInstance(SimpleScope.class);

        final ObservationRegistry registry;

        private final Observation currentObservation;

        @Nullable
        final Scope previousObservationScope;

        SimpleScope(ObservationRegistry registry, Observation current) {
            this.registry = registry;
            this.currentObservation = current;
            this.previousObservationScope = registry.getCurrentObservationScope();
            this.registry.setCurrentObservationScope(this);
        }

        @Override
        public Observation getCurrentObservation() {
            return this.currentObservation;
        }

        @Override
        public void close() {
            if (currentObservation instanceof SimpleObservation) {
                SimpleObservation observation = (SimpleObservation) currentObservation;
                SimpleScope lastScopeForThisObservation = getLastScope(this);
                if (lastScopeForThisObservation != null) {
                    observation.lastScope.put(Thread.currentThread(), lastScopeForThisObservation);
                }
                else {
                    observation.lastScope.remove(Thread.currentThread());
                }
                observation.notifyOnScopeClosed();
            }
            else if (currentObservation != null && !currentObservation.isNoop()) {
                log.debug("Custom observation type was used in combination with SimpleScope - that's unexpected");
            }
            else {
                log.trace("NoOp observation used with SimpleScope");
            }
            this.registry.setCurrentObservationScope(previousObservationScope);
        }

        @Nullable
        private SimpleScope getLastScope(SimpleScope simpleScope) {
            SimpleScope scope = simpleScope;
            do {
                scope = (SimpleScope) scope.previousObservationScope;
            }
            while (scope != null && !this.currentObservation.equals(scope.currentObservation));
            return scope;
        }

        @Override
        public void reset() {
            SimpleScope scope = this;
            if (scope.currentObservation instanceof SimpleObservation) {
                SimpleObservation simpleObservation = (SimpleObservation) scope.currentObservation;
                do {
                    // We don't want to remove any enclosing scopes when resetting
                    // we just want to remove any scopes if they are present (that's why
                    // we're not calling scope#close)
                    simpleObservation.notifyOnScopeReset();
                    scope = (SimpleScope) scope.previousObservationScope;
                }
                while (scope != null);
            }
            registry.setCurrentObservationScope(null);
        }

        /**
         * This method is called e.g. via
         * {@link ObservationThreadLocalAccessor#restore(Observation)}. In that case,
         * we're calling {@link ObservationThreadLocalAccessor#reset()} first, and we're
         * closing all the scopes, HOWEVER those are called on the Observation scope that
         * was present there in thread local at the time of calling the method, NOT on the
         * scope that we want to make current (that one can contain some leftovers from
         * previous scope openings like creation of e.g. Brave scope in the TracingContext
         * that is there inside the Observation's Context.
         *
         * When we want to go back to the enclosing scope and want to make that scope
         * current we need to be sure that there are no remaining scoped objects inside
         * Observation's context. This is why BEFORE rebuilding the scope structure we
         * need to notify the handlers to clear them first (again this is a separate scope
         * to the one that was cleared by the reset method) via calling
         * {@link ObservationHandler#onScopeReset(Context)}.
         */
        @Override
        public void makeCurrent() {
            SimpleScope scope = this;
            do {
                // We don't want to remove any enclosing scopes when resetting
                // we just want to remove any scopes if they are present (that's why we're
                // not calling scope#close)
                if (scope.currentObservation instanceof SimpleObservation) {
                    ((SimpleObservation) scope.currentObservation).notifyOnScopeReset();
                }
                scope = (SimpleScope) scope.previousObservationScope;
            }
            while (scope != null);

            Deque<SimpleScope> scopes = new ArrayDeque<>();
            scope = this;
            do {
                scopes.addFirst(scope);
                scope = (SimpleScope) scope.previousObservationScope;
            }
            while (scope != null);
            for (SimpleScope simpleScope : scopes) {
                if (simpleScope.currentObservation instanceof SimpleObservation) {
                    ((SimpleObservation) simpleScope.currentObservation).notifyOnScopeMakeCurrent();
                }
            }
            this.registry.setCurrentObservationScope(this);
        }

        @Nullable
        @Override
        public Scope getPreviousObservationScope() {
            return this.previousObservationScope;
        }

    }

}
