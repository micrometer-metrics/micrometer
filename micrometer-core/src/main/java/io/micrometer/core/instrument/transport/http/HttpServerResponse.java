/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.instrument.transport.http;

import io.micrometer.core.lang.Nullable;
import io.micrometer.core.instrument.transport.Kind;

/**
 * This API is taken from OpenZipkin Brave.
 *
 * Abstract response type used for parsing and sampling. Represents an HTTP Server
 * response.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public interface HttpServerResponse extends HttpResponse {

    @Nullable
    default HttpServerRequest request() {
        return null;
    }

    @Override
    default Throwable error() {
        return null;
    }

    @Override
    default Kind kind() {
        return Kind.SERVER;
    }

}
