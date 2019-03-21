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

import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.util.function.Tuple2;

public class ReactorNettySender implements HttpSender {
    private final reactor.netty.http.client.HttpClient httpClient;

    public ReactorNettySender(reactor.netty.http.client.HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ReactorNettySender() {
        this(reactor.netty.http.client.HttpClient.create());
    }

    @Override
    public Response send(Request request) {
        Tuple2<Integer, String> response = httpClient.request(toNettyHttpMethod(request.getMethod()))
                .uri(request.getUrl().toString())
                .send(ByteBufFlux.fromString(Mono.just(new String(request.getEntity()))))
                .responseSingle((r, body) -> Mono.just(r.status().code()).zipWith(body.asString().defaultIfEmpty("")))
                .log()
                .block();

        return new Response(response.getT1(), response.getT2());
    }

    private io.netty.handler.codec.http.HttpMethod toNettyHttpMethod(Method method) {
        switch (method) {
            case PUT:
                return io.netty.handler.codec.http.HttpMethod.PUT;
            case POST:
                return io.netty.handler.codec.http.HttpMethod.POST;
            case HEAD:
                return io.netty.handler.codec.http.HttpMethod.HEAD;
            case GET:
                return io.netty.handler.codec.http.HttpMethod.GET;
            case DELETE:
                return io.netty.handler.codec.http.HttpMethod.DELETE;
            case OPTIONS:
                return io.netty.handler.codec.http.HttpMethod.OPTIONS;
            default:
                throw new UnsupportedOperationException("http method " + method.toString() + " is not supported by the reactor netty client");
        }
    }
}
