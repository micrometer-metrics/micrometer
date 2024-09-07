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
package io.micrometer.core.instrument.binder.jersey.server.resources;

import io.micrometer.core.annotation.Timed;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.concurrent.CountDownLatch;

import static java.util.Objects.requireNonNull;

/**
 * @author Michael Weirauch
 */
@Path("/")
@Produces(MediaType.TEXT_PLAIN)
public class TimedResource {

    private final CountDownLatch longTaskRequestStartedLatch;

    private final CountDownLatch longTaskRequestReleaseLatch;

    public TimedResource(CountDownLatch longTaskRequestStartedLatch, CountDownLatch longTaskRequestReleaseLatch) {
        this.longTaskRequestStartedLatch = requireNonNull(longTaskRequestStartedLatch);
        this.longTaskRequestReleaseLatch = requireNonNull(longTaskRequestReleaseLatch);
    }

    @GET
    @Path("not-timed")
    public String notTimed() {
        return "not-timed";
    }

    @GET
    @Path("timed")
    @Timed
    public String timed() {
        return "timed";
    }

    @GET
    @Path("timed-slo")
    @Timed(value = "timedSlo", histogram = true, serviceLevelObjectives = { 0.1, 0.5 })
    public String timedSlo() {
        return "timed";
    }

    @GET
    @Path("multi-timed")
    @Timed("multi1")
    @Timed("multi2")
    public String multiTimed() {
        return "multi-timed";
    }

    /*
     * Async server side processing (AsyncResponse) is not supported in the in-memory test
     * container.
     */
    @GET
    @Path("long-timed")
    @Timed
    @Timed(value = "long.task.in.request", longTask = true)
    public String longTimed() {
        longTaskRequestStartedLatch.countDown();
        try {
            longTaskRequestReleaseLatch.await();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return "long-timed";
    }

    @GET
    @Path("just-long-timed")
    @Timed(value = "long.task.in.request", longTask = true)
    public String justLongTimed() {
        longTaskRequestStartedLatch.countDown();
        try {
            longTaskRequestReleaseLatch.await();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return "long-timed";
    }

    @GET
    @Path("long-timed-unnamed")
    @Timed
    @Timed(longTask = true)
    public String longTimedUnnamed() {
        return "long-timed-unnamed";
    }

}
