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
package io.micrometer.observation.transport;

import io.micrometer.common.lang.NonNull;
import io.micrometer.observation.Observation;

import java.util.Objects;

/**
 * Context used when receiving data over the wire without requiring any confirmation to be
 * sent to sender of the data.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 * @param <C> type of the carrier object
 */
public class ReceiverContext<C> extends Observation.Context {

    private final Propagator.Getter<C> getter;

    private final Kind kind;

    private C carrier;

    /**
     * Creates a new instance of {@link ReceiverContext}.
     * @param getter propagator getter
     * @param kind kind
     */
    public ReceiverContext(@NonNull Propagator.Getter<C> getter, @NonNull Kind kind) {
        this.getter = Objects.requireNonNull(getter, "Getter must be set");
        this.kind = Objects.requireNonNull(kind, "Kind must be set");
    }

    /**
     * Creates a new instance of a {@link Kind#CONSUMER} {@link ReceiverContext}.
     * @param getter propagator getter
     */
    public ReceiverContext(@NonNull Propagator.Getter<C> getter) {
        this(getter, Kind.CONSUMER);
    }

    public C getCarrier() {
        return carrier;
    }

    public void setCarrier(C carrier) {
        this.carrier = carrier;
    }

    public Propagator.Getter<C> getGetter() {
        return getter;
    }

    public Kind getKind() {
        return kind;
    }

}
