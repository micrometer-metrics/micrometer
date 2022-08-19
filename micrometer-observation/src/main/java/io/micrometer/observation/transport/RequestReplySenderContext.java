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
import io.micrometer.common.lang.Nullable;

/**
 * Context used when sending data over the wire with the idea that you'll wait for some
 * response from the recipient.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 * @param <C> type of the carrier object
 * @param <RES> type of the response object
 */
public class RequestReplySenderContext<C, RES> extends SenderContext<C> implements ResponseContext<RES> {

    @Nullable
    private RES response;

    /**
     * Creates a new instance of {@link RequestReplySenderContext}.
     * @param setter propagator setter
     * @param kind kind
     */
    public RequestReplySenderContext(@NonNull Propagator.Setter<C> setter, @NonNull Kind kind) {
        super(setter, kind);
    }

    /**
     * Creates a new instance of a {@link Kind#CLIENT} {@link RequestReplySenderContext}.
     * @param setter propagator setter
     */
    public RequestReplySenderContext(@NonNull Propagator.Setter<C> setter) {
        this(setter, Kind.CLIENT);
    }

    @Override
    @Nullable
    public RES getResponse() {
        return response;
    }

    @Override
    public void setResponse(RES response) {
        this.response = response;
    }

}
