/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.samples;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.GuavaCacheMetrics;
import io.micrometer.core.samples.utils.SampleConfig;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.stream.IntStream;

import static io.netty.buffer.Unpooled.wrappedBuffer;

/**
 * @author Jon Schneider
 */
public class CacheSample {

    private static final int CACHE_SIZE = 10000;

    private static final Cache<String, Integer> guavaCache = CacheBuilder.newBuilder()
        .maximumSize(CACHE_SIZE)
        .recordStats() // required
        .build();

    public static void main(String[] args) {
        MeterRegistry registry = SampleConfig.myMonitoringSystem();
        GuavaCacheMetrics.monitor(registry, guavaCache, "book.guava");

        // read all of Frankenstein
        HttpClient.create()
            .baseUrl("www.gutenberg.org")
            .doOnRequest((req, conn) -> conn.addHandlerLast(wordDecoder()))
            .get()
            .uri("/files/84/84-0.txt")
            .responseContent()
            .asString()
            .delayElements(Duration.ofMillis(10)) // one
                                                  // word
                                                  // per
                                                  // 10
                                                  // ms
            .filter(word -> !word.isEmpty())
            .doOnNext(word -> {
                if (guavaCache.getIfPresent(word) == null)
                    guavaCache.put(word, 1);
            })
            .blockLast();
    }

    // skip things that aren't words, roughly
    private static DelimiterBasedFrameDecoder wordDecoder() {
        return new DelimiterBasedFrameDecoder(256,
                IntStream.of('\r', '\n', ' ', '\t', '.', ',', ';', ':', '-')
                    .mapToObj(delim -> wrappedBuffer(new byte[] { (byte) delim }))
                    .toArray(ByteBuf[]::new));
    }

}
