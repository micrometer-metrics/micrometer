/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.binder.http;

import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.test.TestPortProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.BDDAssertions.then;

class ObservationHttpJakartaServerFilterTests {

    TestObservationRegistry observationRegistry = TestObservationRegistry.create();

    ResteasyDeployment deployment = new ResteasyDeploymentImpl();

    NettyJaxrsServer netty;

    @BeforeEach
    void setupServer() {
        netty = new NettyJaxrsServer();
        netty.setDeployment(deployment);
        netty.setPort(TestPortProvider.getPort());
        netty.setRootResourcePath("");
        netty.setSecurityDomain(null);
        deployment.setApplication(new MyApplication());
        deployment
            .setProviders(Collections.singletonList(new ObservationHttpJakartaServerFilter(observationRegistry, null)));
        netty.start();
    }

    @AfterEach
    void shutdownServer() {
        netty.stop();
    }

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(new HeaderReadingHandler());
    }

    @Test
    void serverFilterShouldWorkWithJakartaServer() {
        try (Client client = ClientBuilder.newClient()) {
            final WebTarget target = client.target("http://localhost:" + netty.getPort() + "/foo");
            try (Response response = target.request().header("foo", "bar").get()) {
                then(response.getStatus()).isEqualTo(200);
                then(response.getHeaders()).isNotEmpty();
                List<Object> header = response.getHeaders().get("baz");
                then(header).hasSize(1).containsOnly("bar");
            }
        }
    }

    static class HeaderReadingHandler implements ObservationHandler<HttpJakartaServerRequestObservationContext> {

        @Override
        public void onStop(HttpJakartaServerRequestObservationContext context) {
            List<String> foo = context.getCarrier().getHeaders().get("foo");
            then(foo).hasSize(1);
            context.getResponse().getHeaders().add("baz", foo.get(0));
        }

        @Override
        public boolean supportsContext(Context context) {
            return context instanceof HttpJakartaServerRequestObservationContext;
        }

    }

    @ApplicationPath("/")
    public static class MyApplication extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Collections.singleton(GreetingResource.class);
        }

    }

    @Path("/foo")
    public static class GreetingResource {

        @GET
        public String get() {
            return "Hello, world!";
        }

    }

}
