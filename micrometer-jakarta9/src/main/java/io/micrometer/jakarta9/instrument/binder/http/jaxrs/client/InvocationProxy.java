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
package io.micrometer.jakarta9.instrument.binder.http.jaxrs.client;

import io.micrometer.observation.Observation.Scope;
import io.micrometer.observation.ObservationRegistry;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Invocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * A wrapper around an {@link jakarta.ws.rs.client.Invocation} or
 * {@link jakarta.ws.rs.client.Invocation.Builder} that closes an
 * {@link io.micrometer.observation.Observation} when an exception is thrown. The
 * Observation is created through {@link ObservationJaxRsHttpClientFilter}.
 *
 * The {@link jakarta.ws.rs.client.ClientRequestFilter} and
 * {@link jakarta.ws.rs.client.ClientResponseFilter} do not support the case where an
 * exception is thrown. Without this wrapping an
 * {@link io.micrometer.observation.Observation} is being opened and never closed (same
 * applies to the scope, thus a {@link ThreadLocal} entry pollutes the thread).
 *
 * Usage example:
 *
 * <pre>
 *     try (Client client = ClientBuilder.newClient()) {
 *             // Add the Observation creating filter
 *             client.register(new ObservationJaxRsHttpClientFilter(observationRegistry, null));
 *             final WebTarget target = client
 *                 .target("http://localhost:12345/connectionReset");
 *             // Remember to wrap the call with the InvocationProxy
 *             try (Response response = InvocationProxy.wrap(target.request(), observationRegistry)
 *                 .get()) {
 *                 // an exception will be attached to the observation, the scope and observation will be closed
 *             }
 *         }
 * </pre>
 *
 * @author Marcin Grzejszczak
 * @since 1.13.0
 */
public final class InvocationProxy implements InvocationHandler {

    private final ObservationRegistry observationRegistry;

    private final Object wrapped;

    private InvocationProxy(ObservationRegistry observationRegistry, Object wrapped) {
        this.observationRegistry = observationRegistry;
        this.wrapped = wrapped;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(this.wrapped, args);
        }
        catch (InvocationTargetException exception) {
            Throwable targetException = exception.getTargetException();
            if (targetException instanceof ProcessingException) {
                Scope scope = observationRegistry.getCurrentObservationScope();
                if (scope != null) {
                    scope.getCurrentObservation().error(exception);
                    scope.getCurrentObservation().stop();
                    scope.close();
                }
            }
            throw targetException;
        }
    }

    /**
     * Wraps an {@link Invocation.Builder}.
     * @param object object to wrap
     * @param observationRegistry observation registry
     * @return {@link io.micrometer.observation.Observation} closing
     * {@link Invocation.Builder}
     */
    @SuppressWarnings("unchecked")
    public static Invocation.Builder wrap(Invocation.Builder object, ObservationRegistry observationRegistry) {
        return (Invocation.Builder) Proxy.newProxyInstance(InvocationProxy.class.getClassLoader(),
                new Class[] { Invocation.Builder.class }, new InvocationProxy(observationRegistry, object));
    }

    /**
     * Wraps an {@link Invocation}.
     * @param object object to wrap
     * @param observationRegistry observation registry
     * @return {@link io.micrometer.observation.Observation} closing {@link Invocation}
     */
    @SuppressWarnings("unchecked")
    public static Invocation wrap(Invocation object, ObservationRegistry observationRegistry) {
        return (Invocation) Proxy.newProxyInstance(InvocationProxy.class.getClassLoader(),
                new Class[] { Invocation.class }, new InvocationProxy(observationRegistry, object));
    }

}
