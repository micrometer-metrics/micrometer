/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.atlas;

import com.netflix.spectator.atlas.AtlasConfig;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Clock;
import org.junit.jupiter.api.Test;
import reactor.ipc.netty.http.server.HttpServer;
import reactor.ipc.netty.tcp.BlockingNettyContext;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AtlasMeterRegistryTest {
    private AtlasConfig config = k -> null;
    private AtlasMeterRegistry registry = new AtlasMeterRegistry(config, Clock.SYSTEM);

    @Issue("#484")
    @Test
    void publishOneLastTimeOnClose() throws InterruptedException {
        URI uri = URI.create(config.uri());
        CountDownLatch latch = new CountDownLatch(1);

        BlockingNettyContext blockingFacade = HttpServer.create(uri.getHost(), uri.getPort())
                .start((req, resp) -> {
                    latch.countDown();
                    return resp.send();
                });
        try {
            registry.close();
            latch.await(10, TimeUnit.SECONDS);
            assertThat(latch.getCount()).isZero();
        } finally {
            blockingFacade.shutdown();
        }
    }
}
