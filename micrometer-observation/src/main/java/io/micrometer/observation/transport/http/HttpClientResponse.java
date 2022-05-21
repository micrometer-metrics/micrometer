/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.observation.transport.http;

import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.transport.Kind;

/**
 * This API is taken from OpenZipkin Brave.
 *
 * Abstract response type used for parsing and sampling. Represents an HTTP Client
 * response.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface HttpClientResponse extends HttpResponse {

    @Nullable
    default HttpClientRequest request() {
        return null;
    }

    @Override
    default Throwable error() {
        return null;
    }

    @Override
    default Kind kind() {
        return Kind.CLIENT;
    }

}
