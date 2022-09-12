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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.binder.jersey.server.DefaultJerseyTagsProvider;
import io.micrometer.core.instrument.binder.jersey.server.MetricsApplicationEventListener;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;

import javax.ws.rs.*;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

class JerseyServerTimingInstrumentationVerificationTests extends HttpServerTimingInstrumentationVerificationTests {

    JerseyTest jerseyTest;

    @Override
    protected URI startInstrumentedServer() throws Exception {
        jerseyTest = new JerseyTest() {
            @Override
            protected Application configure() {
                MetricsApplicationEventListener listener = new MetricsApplicationEventListener(getRegistry(),
                        new DefaultJerseyTagsProvider(), timerName(), true);

                ResourceConfig config = new ResourceConfig();
                config.register(listener);
                config.register(TestResource.class);

                return config;
            }
        };

        jerseyTest.setUp();

        return jerseyTest.target().getUri();
    }

    @Override
    protected void stopInstrumentedServer() throws Exception {
        jerseyTest.tearDown();
    }

    @Path("/")
    @Produces(MediaType.TEXT_PLAIN)
    public static class TestResource {

        @GET
        @Path(InstrumentedRoutes.ROOT)
        public String root() {
            return "hello";
        }

        @GET
        @Path(InstrumentedRoutes.TEMPLATED_ROUTE)
        public String hello(@PathParam("name") String name) {
            return "hello " + name;
        }

        @GET
        @Path(InstrumentedRoutes.REDIRECT)
        public Response redirect() {
            return Response.status(302).location(URI.create("/")).build();
        }

        @POST
        @Path(InstrumentedRoutes.ERROR)
        public Response error() {
            return Response.serverError().build();
        }

    }

}
