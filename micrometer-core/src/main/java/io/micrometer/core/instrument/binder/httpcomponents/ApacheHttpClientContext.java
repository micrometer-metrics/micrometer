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
package io.micrometer.core.instrument.binder.httpcomponents;

import io.micrometer.observation.transport.RequestReplySenderContext;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;

public class ApacheHttpClientContext extends RequestReplySenderContext<HttpRequest, HttpResponse> {

    private final HttpContext apacheHttpContext;

    public ApacheHttpClientContext(HttpRequest request, HttpContext apacheHttpContext) {
        super((httpRequest, key, value) -> {
            if (httpRequest != null) {
                httpRequest.addHeader(key, value);
            }
        });
        setCarrier(request);
        this.apacheHttpContext = apacheHttpContext;
    }

    public HttpContext getApacheHttpContext() {
        return apacheHttpContext;
    }

}
