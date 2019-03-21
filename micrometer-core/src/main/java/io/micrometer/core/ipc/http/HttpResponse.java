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

import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;

import java.util.function.Consumer;

public class HttpResponse {
    public static final String NO_RESPONSE_BODY = "<no response body>";
    private final int code;
    private final String body;

    public HttpResponse(int code, @Nullable String body) {
        this.code = code;
        this.body = StringUtils.isBlank(body) ? NO_RESPONSE_BODY : body;
    }

    public int code() {
        return code;
    }

    public String body() {
        return body;
    }

    public HttpResponse onSuccess(Consumer<HttpResponse> onSuccess) {
        switch (HttpStatusClass.valueOf(code)) {
            case INFORMATIONAL:
            case SUCCESS:
                onSuccess.accept(this);
        }
        return this;
    }

    public HttpResponse onError(Consumer<HttpResponse> onError) {
        switch (HttpStatusClass.valueOf(code)) {
            case CLIENT_ERROR:
            case SERVER_ERROR:
                onError.accept(this);
        }
        return this;
    }

    public boolean isSuccessful() {
        switch (HttpStatusClass.valueOf(code)) {
            case INFORMATIONAL:
            case SUCCESS:
                return true;
            default:
                return false;
        }
    }
}
