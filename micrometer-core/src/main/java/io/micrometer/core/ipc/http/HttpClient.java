/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.core.ipc.http;

import java.net.URL;

public interface HttpClient {
    HttpResponse send(URL url, HttpRequest request) throws Throwable;

    default HttpRequest.Builder post(String uri) {
        return newRequest(uri).withMethod(HttpMethod.POST);
    }

    default HttpRequest.Builder head(String uri) {
        return newRequest(uri).withMethod(HttpMethod.HEAD);
    }

    default HttpRequest.Builder put(String uri) {
        return newRequest(uri).withMethod(HttpMethod.PUT);
    }

    default HttpRequest.Builder get(String uri) {
        return newRequest(uri).withMethod(HttpMethod.GET);
    }

    default HttpRequest.Builder delete(String uri) {
        return newRequest(uri).withMethod(HttpMethod.DELETE);
    }

    default HttpRequest.Builder options(String uri) {
        return newRequest(uri).withMethod(HttpMethod.OPTIONS);
    }

    default HttpRequest.Builder newRequest(String uri) {
        return new HttpRequest.Builder(uri, this);
    }
}
