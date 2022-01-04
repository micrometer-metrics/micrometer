/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.core.ipc.http;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.function.Tuple2;

/**
 * {@link HttpSender} implementation based on the Reactor Netty {@link HttpClient}.
 *
 * @author Jon Schneider
 * @since 1.1.0
 * @deprecated use a different {@link HttpSender} implementation or report your use case for this to the Micrometer project maintainers
 */
@Deprecated
public class ReactorNettySender implements HttpSender {
    private final HttpClient httpClient;

    public ReactorNettySender(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ReactorNettySender() {
        this(HttpClient.create());
    }

    @Override
    public Response send(Request request) {
        Tuple2<Integer, String> response = httpClient
                .request(toNettyHttpMethod(request.getMethod()))
                .uri(request.getUrl().toString())
                .send((httpClientRequest, nettyOutbound) -> {
                    request.getRequestHeaders().forEach(httpClientRequest::addHeader);
                    return nettyOutbound.sendByteArray(Mono.just(request.getEntity()));
                })
                .responseSingle((r, body) -> Mono.just(r.status().code()).zipWith(body.asString().defaultIfEmpty("")))
                .block();

        return new Response(response.getT1(), response.getT2());
    }

    private HttpMethod toNettyHttpMethod(Method method) {
        switch (method) {
            case PUT:
                return HttpMethod.PUT;
            case POST:
                return HttpMethod.POST;
            case HEAD:
                return HttpMethod.HEAD;
            case GET:
                return HttpMethod.GET;
            case DELETE:
                return HttpMethod.DELETE;
            case OPTIONS:
                return HttpMethod.OPTIONS;
            default:
                throw new UnsupportedOperationException("http method " + method.toString() + " is not supported by the reactor netty client");
        }
    }
}
